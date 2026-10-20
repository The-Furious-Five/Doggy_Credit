# ADR-027 · Segundo banco con el contrato estándar de conector

Fecha: 2026-10-20. Estado: aceptado (B-10, FF-15; construido).

## Contexto

Había que demostrar que añadir otro proveedor bancario no cambia el dominio.
`SourceConnector<T>` (`integration/SourceConnector.java`) es el contrato; todo
acceso pasa por `ConnectorGateway`, que exige consentimiento vigente.

## Decisión

`BankDosConnector` implementa el contrato para otro core bancario
(`contracts/connectors/bank-dos.v1.yaml`, traducido por `BankDosJson`) y devuelve el
mismo `BankReport` y el mismo `Source.BANCO` que `BankConnector`. Se elige con
`doggycredit.integration.bank.provider` (`DC_INTEGRATION_BANK_PROVIDER`: `uno`
por defecto o `dos`); nunca ambos activos porque colisionarían en `Source.BANCO`.
El simulador `wiremock-bank-dos` (`simulators/wiremock/bank-dos`, puerto 8092)
está en `infra/compose.yaml` y en `infra/compose.prod.yaml`, inactivo salvo
que se seleccione `dos`.

## Alternativas consideradas

- Fuente nueva `Source.BANCO_DOS`: descartado; scoring y consentimiento tendrían que conocerla.
- Ramas por proveedor dentro de `BankConnector`: descartado; mezcla formatos en una clase.
- Ambos bancos en paralelo: descartado; requeriría agregar y reconciliar por tenant.

## Consecuencias

El proveedor se elige por despliegue, no por tenant. Un tercer banco es una clase,
un esquema en `contracts/connectors/` y un simulador; no cambia el scoring.
