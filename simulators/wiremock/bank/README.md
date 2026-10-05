# Simulador del core bancario (WireMock) — B-01

Todo lo que hay en `mappings/` y `__files/` lo **genera**
`seed/generate_synthetic.py` a partir de `seed/personas-sinteticas.json` (las
mismas 50 personas que el buró). No se edita a mano; ver
[`../bureau/README.md`](../bureau/README.md) para el generador, la regla de
normalización del documento y los arquetipos.

Contrato: [`contracts/connectors/bank.v1.yaml`](../../../contracts/connectors/bank.v1.yaml).
Conector: `integration.BankConnector` (`DC_INTEGRATION_BANK_URL`, por defecto
`http://localhost:8090`).

Coherencia con el buró: los abonos mensuales rondan el ingreso declarado
(las ventas en las PYME) ±10 %, los cargos el egreso, y el saldo promedio de
90 días es bajo en los arquetipos `moroso`, `pyme_morosa` y `sobreendeudado`.

| Documento | Respuesta |
|---|---|
| las 50 personas sembradas | `200` tras ~300 ms |
| cualquiera no sembrado | `404` `{"codigo":"CLIENTE_NO_ENCONTRADO","numeroDocumento":"…"}` |
| `CI-DEMO-0009503` | `503` `{"codigo":"SERVICIO_NO_DISPONIBLE"}` |
| `CI-DEMO-0009504` | `200` tras 15 s (timeout del conector) |

```bash
curl http://localhost:8090/bank/v1/clientes/CI-DEMO-0001001    # 200 JSON
curl http://localhost:8090/bank/v1/clientes/CI-DEMO-0009999    # 404 JSON
```
