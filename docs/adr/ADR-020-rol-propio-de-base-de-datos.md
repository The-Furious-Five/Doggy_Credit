# ADR-020 · La aplicación entra a PostgreSQL con un rol propio, sin `SET ROLE`

Fecha: 2026-10-20. Estado: aceptado (DEBT-018, A-05; construido).

## Contexto

La app iniciaba sesión como `doggy_app`, superusuario por ser el
`POSTGRES_USER` de la imagen, y bajaba a `doggycredit_app` con `SET ROLE` (SPIKE-1).
`SET ROLE` es reversible: un `RESET ROLE` inyectado devolvía el superusuario y
saltaba RLS (`docs/debt-register.md`, DEBT-018).

## Decisión

La migración `V2026_10_04_2100__debt018_app_role_own_login.sql` hace de
`doggycredit_app` un rol `LOGIN` propio, sin superusuario ni `BYPASSRLS`. La app
conecta directo con él; Flyway conserva el usuario administrador. No hay
`SET ROLE`. `ProductionDataSourceWiringTest` comprueba que `RESET ROLE` la deja
en `doggycredit_app`, que RLS sigue aplicando y que no puede asumir el rol
administrador. `RowLevelSecurityCoverageTests` exige `ENABLE`+`FORCE` RLS y
`tenant_isolation` en toda tabla con `tenant_id`.

## Alternativas consideradas

- Mantener `SET ROLE`: descartado, es reversible por SQL.
- Dos pools en la app (admin y app): descartado; el administrador solo es
  necesario para migrar y no debe existir en el proceso en ejecución.
- Seguridad solo en Java (filtros por `tenant_id`): descartado; RLS es la
  segunda barrera que no depende de que cada consulta se acuerde de filtrar.

## Consecuencias

Una inyección SQL queda limitada por RLS. Cada tabla nueva necesita su `GRANT` y
política en la misma migración (DEBT-017); las lecturas entre tenants deben usar
un patrón explícito como el de ADR-019. Hay dos credenciales distintas que operar.
