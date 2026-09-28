#!/usr/bin/env python3
"""Gate de tests omitidos (HAB-02).

Falla si:
  - no hay reportes de tests (un build que no corrió tests no pasa);
  - algún test omitido (skipped) no está en la lista blanca;
  - alguna entrada de la lista blanca está vencida o le falta un campo.

Uso:
  python ci/check_skipped_tests.py [--allowlist RUTA] [DIR_REPORTES ...]
  python ci/check_skipped_tests.py --self-test

Por defecto lee backend/target/surefire-reports y backend/target/failsafe-reports.
Solo stdlib.
"""
import datetime
import glob
import json
import os
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIRS = [os.path.join(ROOT, "backend", "target", d) for d in ("surefire-reports", "failsafe-reports")]
DEFAULT_ALLOWLIST = os.path.join(ROOT, "ci", "skipped-tests-allowlist.json")
REQUIRED = ("test", "reason", "owner", "expires")


def collect(dirs):
    """Devuelve (cantidad de testcases, lista de 'Clase#metodo' omitidos)."""
    total, skipped = 0, []
    for d in dirs:
        for path in sorted(glob.glob(os.path.join(d, "TEST-*.xml"))):
            for tc in ET.parse(path).getroot().iter("testcase"):
                total += 1
                if tc.find("skipped") is not None:
                    skipped.append(f"{tc.get('classname')}#{tc.get('name')}")
    return total, skipped


def check(dirs, allowlist, today):
    """Devuelve la lista de errores (vacía = gate aprobado)."""
    errors, allowed = [], set()
    for i, e in enumerate(allowlist):
        missing = [k for k in REQUIRED if not str(e.get(k, "")).strip()]
        if missing:
            errors.append(f"entrada #{i} de la lista blanca sin {', '.join(missing)}: {e}")
            continue
        try:
            expires = datetime.date.fromisoformat(e["expires"])
        except ValueError:
            errors.append(f"entrada '{e['test']}': 'expires' no es fecha ISO (AAAA-MM-DD): {e['expires']}")
            continue
        if expires < today:
            errors.append(f"entrada '{e['test']}' vencida el {expires} (dueño: {e['owner']}): arreglen el test o renueven con motivo")
            continue
        allowed.add(e["test"])

    total, skipped = collect(dirs)
    if total == 0:
        errors.append(f"no se encontró ningún test en {dirs}: un build sin tests no pasa")
    for t in skipped:
        # Una entrada puede ser 'Clase#metodo' o 'Clase' (todos sus métodos).
        if t not in allowed and t.split("#")[0] not in allowed:
            errors.append(f"test omitido no autorizado: {t}")
    print(f"Tests ejecutados: {total}, omitidos: {len(skipped)}, autorizados en lista blanca: {len(allowed)}")
    return errors


def main(argv):
    allowlist_path = DEFAULT_ALLOWLIST
    if "--allowlist" in argv:
        i = argv.index("--allowlist")
        allowlist_path = argv[i + 1]
        del argv[i:i + 2]
    with open(allowlist_path, encoding="utf-8") as f:
        allowlist = json.load(f)
    errors = check(argv or DEFAULT_DIRS, allowlist, datetime.date.today())
    if errors:
        print("GATE DE TESTS OMITIDOS: FALLA")
        for e in errors:
            print(f"  - {e}")
        print("Ver docs/runbooks/tests-omitidos.md")
        return 1
    print("GATE DE TESTS OMITIDOS: OK")
    return 0


def self_test():
    today = datetime.date(2026, 9, 28)
    with tempfile.TemporaryDirectory() as d:
        assert check([d], [], today), "sin reportes debe fallar"
        with open(os.path.join(d, "TEST-a.xml"), "w", encoding="utf-8") as f:
            f.write('<testsuite><testcase classname="A" name="ok"/>'
                    '<testcase classname="A" name="skip"><skipped/></testcase></testsuite>')
        assert check([d], [], today), "omitido sin autorizar debe fallar"
        entry = {"test": "A#skip", "reason": "r", "owner": "o", "expires": "2026-10-01"}
        assert check([d], [entry], today) == []
        assert check([d], [dict(entry, test="A")], today) == [], "clase completa autoriza sus métodos"
        assert check([d], [dict(entry, expires="2026-09-27")], today), "vencida debe fallar"
        assert check([d], [{k: v for k, v in entry.items() if k != "expires"}], today), "sin expires debe fallar"
        assert check([d], [dict(entry, owner="")], today), "sin dueño debe fallar"
        assert check([d], [dict(entry, expires="pronto")], today), "fecha inválida debe fallar"
    print("self-test OK")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if "--self-test" in sys.argv else main(sys.argv[1:]))
