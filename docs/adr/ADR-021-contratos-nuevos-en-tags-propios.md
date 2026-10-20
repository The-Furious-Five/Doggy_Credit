# ADR-021 · Los contratos nuevos van en tags propios del OpenAPI

Fecha: 2026-10-20. Estado: aceptado (D-10, A-15; construido).

## Contexto

`contracts/openapi/doggycredit-api.v1.yaml` es la fuente de verdad (contract-first);
los clientes Java y Angular se generan de él. `contracts/openapi/spring-generator.json`
usa `interfaceOnly`, `skipDefaultInterface` y `useTags`: cada tag produce una
interfaz en `com.doggycredit.shared.contract.api` y los controladores la
implementan. Sin métodos por defecto, añadir una operación a una interfaz
existente (`TenantApi`, `AdminApi`, `AuditoriaApi`) rompe la compilación de su
controlador hasta que se implemente.

## Decisión

Toda operación contratada antes de su implementación va en un **tag nuevo**
(`revision-manual`, `planes`, `reporte-accesos`, `operador`,
`credenciales-integraciones`). Quien implementa la historia crea el controlador
de la interfaz nueva. Mientras tanto el backend compila y la ruta responde 404.
Los campos nuevos de esquemas existentes son opcionales. Ver
`docs/runbooks/contratos-sprint3-d10.md` y `contratos-sprint4-a15.md`.

## Alternativas consideradas

- Quitar `skipDefaultInterface` (métodos por defecto que responden 501): descartado;
  esconde endpoints sin implementar detrás de código que parece válido.
- Añadir a los tags existentes y dejar el build roto hasta la historia dueña:
  descartado; bloquea a todos los demás.
- Contrato junto con la implementación, sin contract-first: descartado; el
  front y E2E necesitan el contrato antes.

## Consecuencias

Los equipos trabajan en paralelo sin romper `main`. A cambio hay más tags y
controladores que interfaces agrupadas por recurso, y quien implementa debe
acordarse de activar lo que dependa del 404 (por ejemplo, la bandera `bandeja`
de `e2e/tests/support.ts`).
