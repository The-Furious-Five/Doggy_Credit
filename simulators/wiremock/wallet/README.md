# Simulador de la billetera móvil (WireMock) — B-02

Todo lo que hay en `mappings/` y `__files/` lo **genera**
`seed/generate_synthetic.py` a partir de `seed/personas-sinteticas.json` (las
mismas 50 personas que el buró). No se edita a mano; ver
[`../bureau/README.md`](../bureau/README.md) para el generador, la regla de
normalización del documento y los arquetipos.

Contrato: [`contracts/connectors/wallet.v1.yaml`](../../../contracts/connectors/wallet.v1.yaml).
Conector: `integration.WalletConnector` (`DC_INTEGRATION_WALLET_URL`, por
defecto `http://localhost:8091`).

Coherencia con el buró: los montos recibidos son una fracción del ingreso
declarado; el arquetipo `sin_historial` usa mucho más la billetera (25–90
transacciones al mes y 30–60 % del ingreso) que el resto (4–35 y 5–25 %).

| Documento | Respuesta |
|---|---|
| las 50 personas sembradas | `200` tras ~300 ms |
| cualquiera no sembrado | `404` `{"error":"TITULAR_NO_REGISTRADO","documento":"…"}` |
| `CI-DEMO-0009503` | `503` `{"codigo":"SERVICIO_NO_DISPONIBLE"}` |
| `CI-DEMO-0009504` | `200` tras 15 s (timeout del conector) |

```bash
curl http://localhost:8091/wallet/v1/titulares/CI-DEMO-0001003    # 200 JSON
curl http://localhost:8091/wallet/v1/titulares/CI-DEMO-0009999    # 404 JSON
```
