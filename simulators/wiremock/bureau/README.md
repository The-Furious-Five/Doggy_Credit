# Simulador del buró (WireMock)

Todo lo que hay en `mappings/` y `__files/` lo **genera**
`seed/generate_synthetic.py` (SPIKE-4, RF-INT-02). No se edita a mano: el
generador borra y reescribe ambas carpetas completas.

```bash
python seed/generate_synthetic.py           # regenera seed/personas-sinteticas.json + este simulador
python seed/generate_synthetic.py --check   # invariantes + verifica que lo commiteado esté al día
```

Solo stdlib de Python 3; semilla fija, así que dos corridas producen archivos
idénticos byte a byte.

## Fuente única

- `seed/tenants.yaml` → tenants y los 2 `titulares_demo` fijos
  (`CI-DEMO-0001001`, `NIT-DEMO-9001001`), que el generador **lee**, no copia.
- `seed/personas-sinteticas.json` → las 50 personas sintéticas (datos
  financieros incluidos). El buró, el banco (`../bank`) y la billetera
  (`../wallet`) se derivan de este archivo para que los datos sean coherentes
  entre fuentes.

## Regla de normalización del documento (acordada por el equipo)

```
normalize(doc) = trim → MAYÚSCULAS → quitar todo espacio en blanco y todo punto
" ci-demo-0 001.001 "  →  "CI-DEMO-0001001"
```

El simulador responde **solo** al documento normalizado (match exacto de URL).
El conector normaliza antes de llamar; `ci-demo-0001001` recibe 404.

## Documentos

| Rango | Tipo | Cantidad |
|---|---|---|
| `CI-DEMO-0001001` … `CI-DEMO-0001040` | PERSONA | 40 |
| `NIT-DEMO-9001001` … `NIT-DEMO-9001010` | PYME | 10 |

Arquetipos (`perfil_sintetico`), asignados en ciclo sobre el rango:

| Arquetipo | Buró |
|---|---|
| `buen_pagador` | calificación A, mora 0, cuota 10–25 % del ingreso |
| `moroso` | mora 31–150 días (calificación C–F), cuota 25–40 % |
| `sin_historial` | 200 con deuda 0, antigüedad 0, calificación `N` (≠ documento desconocido) |
| `sobreendeudado` | cuota 55–85 % del ingreso, mora 0–20 días (A/B) |
| `pyme_verde` | calificación A, sector y `destino_verde` (VER-*) |
| `pyme_morosa` | mora 31–90 días (C/D) |

La calificación sale de la mora máxima: ≤5 A, ≤30 B, ≤60 C, ≤90 D, ≤120 E, resto F.

## Respuestas especiales

| Documento | Respuesta |
|---|---|
| cualquiera no sembrado | `404` `<ErrorBuro><Codigo>DOCUMENTO_NO_ENCONTRADO</Codigo><Documento>…</Documento></ErrorBuro>` |
| `CI-DEMO-0009503` | `503` `<ErrorBuro><Codigo>SERVICIO_NO_DISPONIBLE</Codigo></ErrorBuro>` |
| `CI-DEMO-0009504` | `200` tras 15 s (para probar timeouts del conector) |

Estos dos documentos de falla **no** son personas y no están en el JSON.

## Contrato y conector

Contrato publicado: [`contracts/connectors/bureau.v1.yaml`](../../../contracts/connectors/bureau.v1.yaml).
Lo consume `integration.BureauConnector` (URL en `DC_INTEGRATION_BUREAU_URL`,
por defecto `http://localhost:8089`). `BureauConnectorContractTests` levanta
este mismo directorio en `wiremock/wiremock:3.9.1` y verifica las 50 personas
del JSON contra el conector: si cambian el generador, esas pruebas avisan.

## Prueba rápida

```bash
curl http://localhost:8089/bureau/v1/informe/CI-DEMO-0001001    # 200 XML
curl http://localhost:8089/bureau/v1/informe/CI-DEMO-0009999    # 404 XML
```
