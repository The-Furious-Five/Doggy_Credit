# `contracts/connectors/`

Contrato publicado de cada fuente simulada (WireMock), en el formato real
que expone ese proveedor (el buró responde XML, a propósito — capítulo 4.5
de la guía de inducción). Alvaro y el equipo suman `bank.v1.yaml` y
`wallet.v1.yaml` en el Sprint 2.

## `bureau.v1.yaml` (RF-INT-02.1)

Buró de crédito: `GET /bureau/v1/informe/{documento}` → XML. Lo consume
`integration.BureauConnector`; la capa anticorrupción
(`integration.internal.BureauXml`) lo traduce al Perfil Financiero 360 v1:

| XML del buró | Canónico v1 (`BureauReport`) |
|---|---|
| `TipoTitular` (`PERSONA`/`PYME`) | `titular.tipo` |
| `AntiguedadSistemaFinancieroMeses` | `titular.antiguedadSistemaFinancieroMeses` |
| `DeudaVigente` + `@moneda` | `endeudamiento[].saldo` (`Dinero`) |
| `CuotaMensual` + `@moneda` | `endeudamiento[].cuota` (`Dinero`) |
| `MoraMaximaUltimos12Meses/@dias` | `endeudamiento[].diasMoraMax12m` |
| `Calificacion/@codigo` A–F | `endeudamiento[].calificacion` (escala ASFI) |
| `Calificacion/@codigo` `N` | `endeudamiento` vacío (sin historial) |
| `FechaCorte` | `endeudamiento[].fechaCorte` |
| — | `endeudamiento[].fuente` = `BURO`, `tipoCredito` = `CONSOLIDADO` |
| cuerpo crudo | `linaje.sha256Crudo` (el crudo no se guarda, ADR-016) |
| `Documento` | se verifica igual al pedido; no sale del conector |

Resultados: informe → `Optional.of(report)`; 404 `DOCUMENTO_NO_ENCONTRADO`
→ `Optional.empty()` (titular sin registro); 5xx, timeout, red o XML que no
cumple el contrato → `SourceUnavailableException(BURO)` (la evaluación
queda PARCIAL). Nunca un mapeo a medias.

Pruebas de contrato contra el simulador real:
`backend/src/test/java/com/doggycredit/integration/BureauConnectorContractTests.java`.
