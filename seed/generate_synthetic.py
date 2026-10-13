#!/usr/bin/env python3
"""Generador sintético determinista de titulares (SPIKE-4, RF-INT-02).

Escribe la única fuente de personas sintéticas (seed/personas-sinteticas.json)
y los stubs de los simuladores del buró, el banco y la billetera
(simulators/wiremock/{bureau,bank,wallet}/). Solo stdlib.
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
BANK = ROOT / "simulators" / "wiremock" / "bank"
BANK_DOS = ROOT / "simulators" / "wiremock" / "bank-dos"
WALLET = ROOT / "simulators" / "wiremock" / "wallet"
SIMULADORES = (BUREAU, BANK, BANK_DOS, WALLET)

SEED = 20260928
MONEDA = "BOB"
FECHA_CORTE = "2026-09-01"
# Últimos 6 meses cerrados antes de la fecha de corte (banco y billetera).
PERIODOS = [f"2026-{m:02d}" for m in range(3, 9)]
N_PERSONAS, N_PYMES = 40, 10
ARQ_PERSONA = ["buen_pagador", "moroso", "sin_historial", "sobreendeudado"]
ARQ_PYME = ["pyme_verde", "pyme_morosa"]
# Documentos que NO son personas: simulan fallas de cada fuente para probar los conectores.
DOC_CAIDA, DOC_LENTO = "CI-DEMO-0009503", "CI-DEMO-0009504"
FALLAS = {
    DOC_CAIDA: {"status": 503, "body": "<ErrorBuro><Codigo>SERVICIO_NO_DISPONIBLE</Codigo></ErrorBuro>"},
    DOC_LENTO: {"status": 200, "fixedDelayMilliseconds": 15000,
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


def meses_antes_del_corte(meses):
    """Primer día del mes que queda `meses` antes del mes de la fecha de corte."""
    total = 2026 * 12 + (9 - 1) - meses
    return f"{total // 12}-{total % 12 + 1:02d}-01"


def banco(p):
    """Core bancario (Sprint 2), coherente con las finanzas del titular.

    RNG propio por documento: sumar fuentes no cambia ni un valor del buró."""
    rng = random.Random(f"{SEED}-banco-{p['documento']}")
    f, u = p["finanzas"], rng.uniform
    abono = float(f.get("ventas_mensuales", f["ingreso_mensual"]))
    cargo = abono - float(f["ingreso_mensual"]) + float(f["egreso_mensual"])
    saldo_x = {"sobreendeudado": u(0.05, 0.3), "moroso": u(0.1, 0.5), "pyme_morosa": u(0.1, 0.5)}.get(
        p["perfil_sintetico"], u(0.4, 2.0))
    antiguedad = rng.randint(6, 180)
    return {
        "tipo_cuenta": "CUENTA_CORRIENTE" if p["tipo"] == "PYME" else "CAJA_AHORRO",
        "fecha_apertura": meses_antes_del_corte(antiguedad),
        "antiguedad_cuenta_meses": antiguedad,
        "saldo_promedio_3m": money(round(abono * saldo_x, -1)),
        "abonos_mensuales": [money(round(abono * u(0.9, 1.1), -1)) for _ in PERIODOS],
        "cargos_mensuales": [money(round(cargo * u(0.9, 1.1), -1)) for _ in PERIODOS],
    }


def billetera(p):
    """Billetera móvil (Sprint 2): cantidad y montos de los últimos 6 meses."""
    rng = random.Random(f"{SEED}-billetera-{p['documento']}")
    u = rng.uniform
    # Sin historial bancario, la billetera concentra más movimientos.
    intensivo = p["perfil_sintetico"] == "sin_historial"
    base = float(p["finanzas"]["ingreso_mensual"]) * (u(0.3, 0.6) if intensivo else u(0.05, 0.25))
    antiguedad = rng.randint(3, 72)
    meses = []
    for periodo in PERIODOS:
        recibido = round(base * u(0.8, 1.2), -1)
        meses.append({"periodo": periodo,
                      "movimientos": rng.randint(25, 90) if intensivo else rng.randint(4, 35),
                      "recibido": money(recibido),
                      "enviado": money(round(recibido * u(0.7, 1.0), -1))})
    return {
        "fecha_alta": meses_antes_del_corte(antiguedad),
        "antiguedad_meses": antiguedad,
        "saldo_promedio": money(round(base * u(0.1, 0.6), -1)),
        "meses": meses,
    }


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
        p.update(banco=banco(p), billetera=billetera(p))
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


def resumen_banco(p):
    """Formato del core bancario (contracts/connectors/bank.v1.yaml)."""
    b = p["banco"]
    return dump({
        "numeroDocumento": p["documento"],
        "fechaCorte": FECHA_CORTE,
        "cuentas": [{"producto": b["tipo_cuenta"], "moneda": MONEDA, "fechaApertura": b["fecha_apertura"],
                     "saldoPromedio90d": b["saldo_promedio_3m"]}],
        "movimientosMensuales": [{"periodo": per, "totalAbonos": a, "totalCargos": c}
                                 for per, a, c in zip(PERIODOS, b["abonos_mensuales"], b["cargos_mensuales"])],
    })


def resumen_banco_dos(p):
    """Formato del segundo core bancario (B-10, contracts/connectors/bank-dos.v1.yaml).

    Mismo titular y mismas cifras que `resumen_banco` (ambos bancos describen
    al mismo cliente), pero con la forma y el vocabulario propios de este otro
    proveedor: demuestra que el conector estándar (`SourceConnector`) no exige
    ningún formato de cable en particular, solo el canónico de salida."""
    b = p["banco"]
    producto = "AHORRO" if b["tipo_cuenta"] == "CAJA_AHORRO" else "CORRIENTE"
    return dump({
        "documentoCliente": p["documento"],
        "corte": FECHA_CORTE,
        "cuenta": {"tipoProducto": producto, "moneda": MONEDA, "fechaDesde": b["fecha_apertura"],
                   "saldoPromedio3Meses": b["saldo_promedio_3m"]},
        "movimientos": [{"mes": per, "creditos": a, "debitos": c}
                        for per, a, c in zip(PERIODOS, b["abonos_mensuales"], b["cargos_mensuales"])],
    })


def movimientos_billetera(p):
    """Formato de la billetera móvil (contracts/connectors/wallet.v1.yaml)."""
    w = p["billetera"]
    return dump({
        "titular": {"documento": p["documento"], "fechaAlta": w["fecha_alta"]},
        "moneda": MONEDA,
        "fechaCorte": FECHA_CORTE,
        "saldoPromedio": w["saldo_promedio"],
        "resumenMensual": [{"mes": m["periodo"], "cantidadTransacciones": m["movimientos"],
                            "montoRecibido": m["recibido"], "montoEnviado": m["enviado"]} for m in w["meses"]],
    })


def dump(obj):
    return json.dumps(obj, ensure_ascii=False, indent=2) + "\n"


def mapping(url, response, content_type="application/xml"):
    return dump({"request": {"method": "GET", "url": url},
                 "response": {"headers": {"Content-Type": content_type}, **response}})


def simulador_json(files, raiz, prefijo, cuerpo, personas, no_encontrado):
    """Stubs JSON de una fuente (banco, billetera), con las mismas fallas que el buró."""
    for p in personas:
        doc = p["documento"]
        files[raiz / "__files" / f"{doc}.json"] = cuerpo(p)
        files[raiz / "mappings" / f"{doc}.json"] = mapping(
            prefijo + doc, {"status": 200, "bodyFileName": f"{doc}.json", "fixedDelayMilliseconds": 300},
            "application/json")
    files[raiz / "mappings" / f"{DOC_CAIDA}.json"] = mapping(
        prefijo + DOC_CAIDA, {"status": 503, "jsonBody": {"codigo": "SERVICIO_NO_DISPONIBLE"}}, "application/json")
    files[raiz / "mappings" / f"{DOC_LENTO}.json"] = mapping(
        prefijo + DOC_LENTO, {"status": 200, "fixedDelayMilliseconds": 15000,
                              "jsonBody": {"codigo": "RESPUESTA_TARDIA"}}, "application/json")
    # Fallback: cualquier documento no sembrado -> 404 (nunca un titular inventado).
    files[raiz / "mappings" / "no-encontrado.json"] = dump({
        "priority": 10,
        "request": {"method": "GET", "urlPathPattern": prefijo + ".*"},
        "response": {"status": 404, "headers": {"Content-Type": "application/json"},
                     "body": json.dumps(no_encontrado, ensure_ascii=False) + "\n"}})


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
            f"/bureau/v1/informe/{doc}",
            {"status": 200, "bodyFileName": f"informe-{doc}.xml", "fixedDelayMilliseconds": 300})
    for doc, r in FALLAS.items():
        files[BUREAU / "mappings" / f"informe-{doc}.json"] = mapping(f"/bureau/v1/informe/{doc}", r)
    # Fallback: cualquier documento no sembrado -> 404 (nunca un "buen pagador" silencioso).
    files[BUREAU / "mappings" / "informe-no-encontrado.json"] = dump({
        "priority": 10,
        "request": {"method": "GET", "urlPathPattern": "/bureau/v1/informe/.*"},
        "response": {"status": 404, "headers": {"Content-Type": "application/xml"},
                     "body": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<ErrorBuro><Codigo>DOCUMENTO_NO_ENCONTRADO</Codigo>"
                             "<Documento>{{request.path.[3]}}</Documento></ErrorBuro>\n"}})
    simulador_json(files, BANK, "/bank/v1/clientes/", resumen_banco, personas,
                   {"codigo": "CLIENTE_NO_ENCONTRADO", "numeroDocumento": "{{request.path.[3]}}"})
    simulador_json(files, BANK_DOS, "/bank-dos/v1/cuentas/", resumen_banco_dos, personas,
                   {"codigo": "CLIENTE_INEXISTENTE", "documentoCliente": "{{request.path.[3]}}"})
    simulador_json(files, WALLET, "/wallet/v1/titulares/", movimientos_billetera, personas,
                   {"error": "TITULAR_NO_REGISTRADO", "documento": "{{request.path.[3]}}"})
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
        assert BANK / "mappings" / f"{d}.json" in files and WALLET / "mappings" / f"{d}.json" in files
    for p in personas:
        b, w = p["banco"], p["billetera"]
        assert len(b["abonos_mensuales"]) == len(b["cargos_mensuales"]) == len(w["meses"]) == 6, p["documento"]
        assert all(float(x) > 0 for x in b["abonos_mensuales"] + b["cargos_mensuales"]), p["documento"]
        assert b["antiguedad_cuenta_meses"] > 0 and w["antiguedad_meses"] > 0, p["documento"]
    assert meses_antes_del_corte(1) == "2026-08-01" and meses_antes_del_corte(9) == "2025-12-01"
    assert normalize("  ci-demo-0 001.001 ") == "CI-DEMO-0001001"
    assert normalize("nit-demo-9.001.001") == "NIT-DEMO-9001001"
    assert normalize("CI-DEMO-0001001\t") == "CI-DEMO-0001001"
    assert build_files()[1] == files, "no determinista"
    stale = [str(p) for p, c in files.items() if not p.exists() or p.read_text(encoding="utf-8") != c]
    stale += [str(p) for p in generated_on_disk() if p not in files]
    assert not stale, f"archivos desactualizados, regenerar: {stale}"
    print(f"OK: {len(personas)} personas, {len(files)} archivos al día")


def generated_on_disk():
    return [f for raiz in SIMULADORES for sub in ("mappings", "__files") for f in (raiz / sub).glob("*.*")]


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
