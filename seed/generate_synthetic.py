#!/usr/bin/env python3
"""Generador sintético determinista de titulares (SPIKE-4, RF-INT-02).

Escribe la única fuente de personas sintéticas (seed/personas-sinteticas.json)
y los stubs del simulador del buró (simulators/wiremock/bureau/). Solo stdlib.
Todos los datos son ficticios (RN-20).

    python seed/generate_synthetic.py           # regenera los archivos
    python seed/generate_synthetic.py --check   # verifica invariantes y que lo commiteado esté al día
"""
import json
import random
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TENANTS_YAML = ROOT / "seed" / "tenants.yaml"
PERSONAS_JSON = ROOT / "seed" / "personas-sinteticas.json"
BUREAU = ROOT / "simulators" / "wiremock" / "bureau"

SEED = 20260928
MONEDA = "BOB"
FECHA_CORTE = "2026-09-01"
N_PERSONAS, N_PYMES = 40, 10
ARQ_PERSONA = ["buen_pagador", "moroso", "sin_historial", "sobreendeudado"]
ARQ_PYME = ["pyme_verde", "pyme_morosa"]
# Documentos que NO son personas: simulan fallas del buró para probar el conector.
FALLAS = {
    "CI-DEMO-0009503": {"status": 503, "body": "<ErrorBuro><Codigo>SERVICIO_NO_DISPONIBLE</Codigo></ErrorBuro>"},
    "CI-DEMO-0009504": {"status": 200, "fixedDelayMilliseconds": 15000,
                        "body": "<ErrorBuro><Codigo>RESPUESTA_TARDIA</Codigo></ErrorBuro>"},
}

NOMBRES = ["Justina", "Wilma", "Rene", "Teodoro", "Sonia", "Ariel", "Lourdes", "Efrain",
           "Ximena", "Gonzalo", "Delia", "Hernan", "Paola", "Rolando", "Celia", "Mauricio"]
APELLIDOS = ["Choque", "Mamani", "Quispe", "Condori", "Flores", "Vargas", "Apaza", "Huanca",
             "Rojas", "Limachi", "Ticona", "Colque", "Arce", "Paz", "Villca", "Guarachi"]
SECTORES_VERDES = [("energia_solar", "VER-SOL"), ("eficiencia_energetica", "VER-EFI"),
                   ("movilidad_electrica", "VER-MOV"), ("riego_tecnificado", "VER-AGU"),
                   ("agricultura_sostenible", "VER-AGR"), ("reciclaje", "VER-RES")]
SECTORES = ["comercio", "textil", "metalmecanica", "alimentos", "transporte"]


def normalize(doc):
    """Regla única del equipo: trim, mayúsculas, sin espacios ni puntos."""
    return re.sub(r"[\s.]", "", doc.strip().upper())


def read_tenants_yaml():
    """Parseo mínimo (sin PyYAML): claves de tenants y titulares_demo."""
    text = TENANTS_YAML.read_text(encoding="utf-8")
    tenants = re.findall(r"^  - key: (\S+)\s*\n\s+tenant_id:", text, re.M)
    demo = []
    for block in text.split("\ntitulares_demo:")[1].split("\n  - ")[1:]:
        demo.append(dict(re.findall(r'(\w+): "?([^"\n]*)"?', block)))
    return tenants, demo


def money(x):
    return f"{x:.2f}"


def calificacion(mora):
    """Escala sintética estilo ASFI a partir de la mora máxima (días)."""
    for limite, codigo in ((5, "A"), (30, "B"), (60, "C"), (90, "D"), (120, "E")):
        if mora <= limite:
            return codigo
    return "F"


def finanzas(rng, perfil):
    """Atributos financieros coherentes; banco y billetera (Sprint 2) derivan de aquí."""
    u = rng.uniform
    f = {}
    if perfil.startswith("pyme"):
        ventas = round(u(20000, 120000), -2)
        ingreso = round(ventas * u(0.15, 0.30), -1)
        f["ventas_mensuales"] = money(ventas)
        f["antiguedad_negocio_meses"] = rng.randint(18, 180)
    else:
        ingreso = round(u(2500, 12000), -1)
    ratio_cuota, deuda_x, mora = {
        "buen_pagador": (u(0.10, 0.25), u(4, 18), 0),
        "moroso": (u(0.25, 0.40), u(6, 24), rng.randint(31, 150)),
        "sin_historial": (0, 0, 0),
        "sobreendeudado": (u(0.55, 0.85), u(30, 60), rng.randint(0, 20)),
        "pyme_verde": (u(0.10, 0.20), u(8, 24), 0),
        "pyme_morosa": (u(0.30, 0.45), u(10, 30), rng.randint(31, 90)),
    }[perfil]
    cuota = round(ingreso * ratio_cuota, -1)
    f.update(
        ingreso_mensual=money(ingreso),
        egreso_mensual=money(cuota + round(ingreso * u(0.40, 0.65), -1)),
        deuda_vigente=money(round(cuota * deuda_x, -1)),
        cuota_mensual=money(cuota),
        mora_max_12m_dias=mora,
        antiguedad_sf_meses=0 if perfil == "sin_historial" else rng.randint(6, 120),
        calificacion="N" if perfil == "sin_historial" else calificacion(mora),
    )
    return f


def build_personas():
    rng = random.Random(SEED)
    tenants, demo = read_tenants_yaml()
    demo_by_doc = {normalize(d["documento"]): d for d in demo}
    personas = []
    specs = [("PERSONA", f"CI-DEMO-{1001 + i:07d}", ARQ_PERSONA[i % len(ARQ_PERSONA)]) for i in range(N_PERSONAS)]
    specs += [("PYME", f"NIT-DEMO-{9001001 + i:07d}", ARQ_PYME[i % len(ARQ_PYME)]) for i in range(N_PYMES)]
    for tipo, doc, perfil in specs:
        d = demo_by_doc.get(doc)
        if d:
            perfil = d["perfil_sintetico"]
        apellido = rng.choice(APELLIDOS)
        p = {"documento": doc, "tipo": tipo}
        f = finanzas(rng, perfil)
        if tipo == "PYME":
            sector, verde = rng.choice(SECTORES_VERDES) if perfil == "pyme_verde" else (rng.choice(SECTORES), None)
            nombre = f"{sector.replace('_', ' ').capitalize()} {apellido} SRL (demo, ficticia)"
            f.update(sector=sector, destino_verde=verde)
        else:
            nombre = f"{rng.choice(NOMBRES)} {apellido} {rng.choice(APELLIDOS)} (demo, ficticia)"
        p.update(
            nombre=d["nombre"] if d else nombre,
            tenant=d["tenant"] if d else rng.choice(tenants),
            perfil_sintetico=perfil,
            moneda=MONEDA,
            finanzas=f,
        )
        personas.append(p)
    return personas


def informe_xml(p):
    f = p["finanzas"]
    desc = {"A": "Sin mora reportada", "N": "Sin historial crediticio"}.get(f["calificacion"], "Mora reportada")
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- Respuesta sintética del simulador del buró (RN-20: todo dato es ficticio). Generado por seed/generate_synthetic.py. -->
<InformeCrediticio>
  <Documento>{p["documento"]}</Documento>
  <TipoTitular>{p["tipo"]}</TipoTitular>
  <Calificacion codigo="{f["calificacion"]}">{desc}</Calificacion>
  <DeudaVigente moneda="{MONEDA}">{f["deuda_vigente"]}</DeudaVigente>
  <CuotaMensual moneda="{MONEDA}">{f["cuota_mensual"]}</CuotaMensual>
  <MoraMaximaUltimos12Meses dias="{f["mora_max_12m_dias"]}"/>
  <AntiguedadSistemaFinancieroMeses>{f["antiguedad_sf_meses"]}</AntiguedadSistemaFinancieroMeses>
  <FechaCorte>{FECHA_CORTE}</FechaCorte>
</InformeCrediticio>
"""


def dump(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n"


def mapping(doc, response):
    return dump({"request": {"method": "GET", "url": f"/bureau/v1/informe/{doc}"},
                 "response": {"headers": {"Content-Type": "application/xml"}, **response}})


def build_files():
    """Devuelve {ruta: contenido} de todo lo generado."""
    personas = build_personas()
    files = {PERSONAS_JSON: dump({
        "_comentario": "GENERADO por seed/generate_synthetic.py; no editar a mano. Datos ficticios (RN-20).",
        "version": 1, "semilla": SEED, "fecha_corte": FECHA_CORTE, "personas": personas})}
    for p in personas:
        doc = p["documento"]
        files[BUREAU / "__files" / f"informe-{doc}.xml"] = informe_xml(p)
        files[BUREAU / "mappings" / f"informe-{doc}.json"] = mapping(
            doc, {"status": 200, "bodyFileName": f"informe-{doc}.xml", "fixedDelayMilliseconds": 300})
    for doc, r in FALLAS.items():
        files[BUREAU / "mappings" / f"informe-{doc}.json"] = mapping(doc, r)
    # Fallback: cualquier documento no sembrado -> 404 (nunca un "buen pagador" silencioso).
    files[BUREAU / "mappings" / "informe-no-encontrado.json"] = dump({
        "priority": 10,
        "request": {"method": "GET", "urlPathPattern": "/bureau/v1/informe/.*"},
        "response": {"status": 404, "headers": {"Content-Type": "application/xml"},
                     "body": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ErrorBuro><Codigo>DOCUMENTO_NO_ENCONTRADO</Codigo>"
                             "<Documento>{{request.path.[3]}}</Documento></ErrorBuro>\n"}})
    return personas, files


def check():
    personas, files = build_files()
    _, demo = read_tenants_yaml()
    docs = [p["documento"] for p in personas]
    assert len(personas) == 50, len(personas)
    assert len(set(docs)) == 50, "documentos duplicados"
    assert all(d == normalize(d) and re.fullmatch(r"[A-Z0-9-]+", d) for d in docs)
    for d in demo:
        p = next(p for p in personas if p["documento"] == normalize(d["documento"]))
        assert (p["nombre"], p["tenant"], p["perfil_sintetico"], p["tipo"]) == \
               (d["nombre"], d["tenant"], d["perfil_sintetico"], d["tipo"]), p
    for d in docs:
        assert BUREAU / "mappings" / f"informe-{d}.json" in files
    assert normalize("  ci-demo-0 001.001 ") == "CI-DEMO-0001001"
    assert normalize("nit-demo-9.001.001") == "NIT-DEMO-9001001"
    assert normalize("CI-DEMO-0001001\t") == "CI-DEMO-0001001"
    assert build_files()[1] == files, "no determinista"
    stale = [str(p) for p, c in files.items() if not p.exists() or p.read_text(encoding="utf-8") != c]
    stale += [str(p) for p in generated_on_disk() if p not in files]
    assert not stale, f"archivos desactualizados, regenerar: {stale}"
    print(f"OK: {len(personas)} personas, {len(files)} archivos al día")


def generated_on_disk():
    return list((BUREAU / "mappings").glob("*.json")) + list((BUREAU / "__files").glob("*.xml"))


def main():
    if "--check" in sys.argv:
        return check()
    _, files = build_files()
    for p in generated_on_disk():
        p.unlink()  # borra stubs viejos para no dejar mappings huérfanos
    for p, content in files.items():
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8", newline="\n")
    print(f"escritos {len(files)} archivos")


if __name__ == "__main__":
    main()
