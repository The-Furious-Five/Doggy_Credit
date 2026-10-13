# `contracts/connectors/`

Contrato publicado de cada fuente simulada (WireMock), en el formato real
que expone ese proveedor (el buró responde XML, a propósito — capítulo 4.5
de la guía de inducción; banco y billetera, JSON). Los tres simuladores los
genera `seed/generate_synthetic.py` a partir de las mismas 50 personas de
`seed/personas-sinteticas.json`, así que los datos son coherentes entre fuentes.

| Fuente | Contrato | Simulador (Compose) | Puerto host | Conector |
|---|---|---|---|---|
| Buró | `bureau.v1.yaml` | `wiremock-bureau` | 8089 | `integration.BureauConnector` |
| Core bancario | `bank.v1.yaml` | `wiremock-bank` | 8090 | `integration.BankConnector` |
| Billetera móvil | `wallet.v1.yaml` | `wiremock-wallet` | 8091 | `integration.WalletConnector` |
| Segundo core bancario | `bank-dos.v1.yaml` | `wiremock-bank-dos` | 8092 | `integration.BankDosConnector` |

Las tres comparten los documentos de falla: `CI-DEMO-0009503` → 503 y
`CI-DEMO-0009504` → 200 recién a los 15 s. Cualquier documento no sembrado → 404.

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

## `bank.v1.yaml` (B-01, B-03)

Core bancario: `GET /bank/v1/clientes/{documento}` → JSON con las cuentas,
su saldo promedio de 90 días y los abonos y cargos de los últimos 6 meses
cerrados. La capa anticorrupción (`integration.internal.BankJson`) lo traduce
al Perfil Financiero 360 v1:

| JSON del banco | Canónico v1 (`BankReport`) |
|---|---|
| `cuentas[].producto` (`CAJA_AHORRO`/`CUENTA_CORRIENTE`) | `cuentas[].tipo` |
| `cuentas[].moneda` | `cuentas[].moneda` |
| `cuentas[].saldoPromedio90d` | `cuentas[].saldoPromedio3m` (`Dinero`) |
| `fechaCorte` − `cuentas[].fechaApertura` | `cuentas[].antiguedadMeses` (meses completos) |
| `movimientosMensuales[].totalAbonos` | `flujos.ingresosMensuales[]`, `origen` = `BANCO` |
| `movimientosMensuales[].totalCargos` | `flujos.egresosMensuales[]` |
| cuerpo crudo | `linaje.sha256Crudo` (el crudo no se guarda, ADR-016) |
| `numeroDocumento` | se verifica igual al pedido; no sale del conector |

Resultados: resumen → `Optional.of(report)`; 404 `CLIENTE_NO_ENCONTRADO`
→ `Optional.empty()` (sin cuenta en el banco); 5xx, timeout, red o JSON que no
cumple el contrato → `SourceUnavailableException(BANCO)`.

Pruebas de contrato: `backend/src/test/java/com/doggycredit/integration/BankConnectorContractTests.java`.

## `wallet.v1.yaml` (B-02, B-05)

Billetera móvil: `GET /wallet/v1/titulares/{documento}` → JSON con la cantidad
de transacciones y los montos recibidos y enviados de los últimos 6 meses
cerrados. Capa anticorrupción: `integration.internal.WalletJson`.

| JSON de la billetera | Canónico v1 (`WalletReport`) |
|---|---|
| `fechaCorte` − `titular.fechaAlta` | `wallet.antiguedadMeses` |
| `saldoPromedio` + `moneda` | `wallet.saldoPromedio` (`Dinero`) |
| promedio de `resumenMensual[].cantidadTransacciones` | `wallet.transaccionesMensuales` |
| `resumenMensual[]` | `meses[]` (periodo, transacciones, recibido, enviado) |
| `resumenMensual[].montoRecibido` | `flujos.ingresosMensuales[]` cuando no hay banco, `origen` = `WALLET` |
| cuerpo crudo | `linaje.sha256Crudo` |
| `titular.documento` | se verifica igual al pedido; no sale del conector |

Resultados: movimientos → `Optional.of(report)`; 404 `TITULAR_NO_REGISTRADO`
→ `Optional.empty()` (sin billetera); 5xx, timeout, red o JSON inválido →
`SourceUnavailableException(WALLET)`.

Pruebas de contrato: `backend/src/test/java/com/doggycredit/integration/WalletConnectorContractTests.java`.

## `bank-dos.v1.yaml` (B-10)

Segundo core bancario: `GET /bank-dos/v1/cuentas/{documento}` → JSON con su
propio vocabulario y forma (otro proveedor, otro contrato de cable), sobre
los mismos titulares que `bank.v1.yaml`. La capa anticorrupción
(`integration.internal.BankDosJson`) lo traduce al mismo canónico `BankReport`
que el primer banco:

| JSON del segundo banco | Canónico v1 (`BankReport`) |
|---|---|
| `cuenta.tipoProducto` (`AHORRO`/`CORRIENTE`) | `cuentas[].tipo` (`CAJA_AHORRO`/`CUENTA_CORRIENTE`) |
| `cuenta.moneda` | `cuentas[].moneda` |
| `cuenta.saldoPromedio3Meses` | `cuentas[].saldoPromedio3m` (`Dinero`) |
| `corte` − `cuenta.fechaDesde` | `cuentas[].antiguedadMeses` (meses completos) |
| `movimientos[].creditos` | `flujos.ingresosMensuales[]`, `origen` = `BANCO` |
| `movimientos[].debitos` | `flujos.egresosMensuales[]` |
| cuerpo crudo | `linaje.sha256Crudo` (el crudo no se guarda, ADR-016) |
| `documentoCliente` | se verifica igual al pedido; no sale del conector |

Resultados: cuenta → `Optional.of(report)`; 404 `CLIENTE_INEXISTENTE` →
`Optional.empty()` (sin cuenta en el banco); 5xx, timeout, red o JSON que no
cumple el contrato → `SourceUnavailableException(BANCO)`.

Mismo `Source.BANCO` que `BankConnector`: cuál de los dos conectores está
activo es un cambio de configuración (`doggycredit.integration.bank.provider`,
`uno` o `dos`), no de código — demuestra que un proveedor nuevo entra sin
tocar nada fuera de su propio archivo (FF-15).

Pruebas de contrato: `backend/src/test/java/com/doggycredit/integration/BankDosConnectorContractTests.java`.
