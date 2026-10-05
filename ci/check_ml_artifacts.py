#!/usr/bin/env python3
"""Gate de artefactos del modelo de scoring (D-07, paga DEBT-016).

Se corre después de `ml/training/train.py`. Falla si:
  - el .onnx o el golden set que sirve/prueba el backend no coinciden, por
    SHA-256, con los que acaba de producir el entrenamiento en ml/model/;
  - backend/ trae un .onnx de otra versión que la de model_card.json
    (artefactos viejos olvidados).

`train.py` produce los mismos bytes en cualquier plataforma (ver
PARAM_DECIMALS en train.py), así que un hash distinto significa que alguien
reentrenó sin copiar, copió sin reentrenar o editó un artefacto a mano.

Uso:
  python ci/check_ml_artifacts.py
  python ci/check_ml_artifacts.py --self-test

Solo stdlib.
"""
import glob
import hashlib
import json
import os
import shutil
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def check(root):
    """Devuelve la lista de errores (vacía si todo coincide)."""
    with open(os.path.join(root, "ml", "model", "model_card.json"), encoding="utf-8") as f:
        version = json.load(f)["modelVersion"]

    onnx = f"scoring-{version}.onnx"
    pairs = [
        (f"ml/model/{onnx}", f"backend/src/main/resources/models/{onnx}"),
        ("ml/model/golden_set.json", "backend/src/test/resources/golden_set.json"),
    ]
    errors = []
    for trained, copied in pairs:
        a, b = os.path.join(root, trained), os.path.join(root, copied)
        missing = [p for p, full in ((trained, a), (copied, b)) if not os.path.isfile(full)]
        if missing:
            errors.append(f"falta {', '.join(missing)}")
            continue
        ha, hb = sha256(a), sha256(b)
        print(f"{trained}: {ha}")
        print(f"{copied}: {hb}")
        if ha != hb:
            errors.append(f"{copied} no coincide con {trained}")

    models_dir = os.path.join(root, "backend", "src", "main", "resources", "models")
    for path in sorted(glob.glob(os.path.join(models_dir, "scoring-*.onnx"))):
        if os.path.basename(path) != onnx:
            errors.append(
                f"backend/ trae {os.path.basename(path)}, pero model_card.json dice {version}"
            )
    return errors


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        def write(rel, content):
            path = os.path.join(tmp, rel)
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(content)

        write("ml/model/model_card.json", b'{"modelVersion": "v9"}')
        write("ml/model/scoring-v9.onnx", b"modelo")
        write("ml/model/golden_set.json", b"[]")
        write("backend/src/main/resources/models/scoring-v9.onnx", b"modelo")
        write("backend/src/test/resources/golden_set.json", b"[]")
        assert check(tmp) == [], "artefactos idénticos deben pasar"

        write("backend/src/main/resources/models/scoring-v9.onnx", b"otro")
        assert len(check(tmp)) == 1, "un .onnx distinto debe fallar"

        write("backend/src/main/resources/models/scoring-v9.onnx", b"modelo")
        write("backend/src/main/resources/models/scoring-v8.onnx", b"viejo")
        assert len(check(tmp)) == 1, "un .onnx de otra versión debe fallar"

        os.remove(os.path.join(tmp, "backend/src/main/resources/models/scoring-v8.onnx"))
        shutil.rmtree(os.path.join(tmp, "backend/src/test"))
        assert len(check(tmp)) == 1, "un golden set faltante debe fallar"
    print("self-test OK")


def main(argv):
    if "--self-test" in argv:
        self_test()
        return 0
    errors = check(ROOT)
    if errors:
        print("\nGATE DE ARTEFACTOS ML: FALLA")
        for e in errors:
            print(f"  - {e}")
        print(
            "\nPara arreglarlo: corre ml/training/train.py, copia el .onnx y el golden set"
            " a backend/ (ver ml/README.md) y commitea los cuatro archivos."
        )
        return 1
    print("\nGATE DE ARTEFACTOS ML: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
