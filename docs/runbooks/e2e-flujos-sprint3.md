# E2E de los flujos del Sprint 3 (E-12 / LOS-126)

Dos escenarios nuevos en `e2e/tests/`, contra el Compose completo y sin mocks de
la API, como los de E-06. Cada uno espera una historia de otro integrante; hasta
entonces queda en `test.fixme` y el CI lo informa como omitido.

| Escenario | Archivo | Espera | Interruptor |
| --- | --- | --- | --- |
| Analista resuelve un caso en revisión manual con override justificado | `manual-review.spec.ts` | **C-10** (LOS-111), backend de la bandeja | `bandeja` |
| Admin pasa de Gratis a Pro y la función Pro se habilita en ≤ 1 min sin volver a iniciar sesión | `plan-upgrade.spec.ts` | **A-11** (LOS-115), operador sembrado | `cambioDePlan` |

Los interruptores están en `e2e/tests/support.ts` (`merged`). **Quien fusiona la
historia pone su interruptor en `true` en el mismo PR.** Desde ahí el escenario
corre en el job **6 · Flujo E2E** de cada PR y de `main`, que es el criterio de
E-12. Para probarlo antes en la rama de la historia:

```bash
E2E_DEPENDENCIAS=bandeja npx playwright test tests/manual-review.spec.ts
```

## Lo que necesita C-10 para que pase `manual-review.spec.ts`

Implementar `RevisionManualApi` (ya se genera desde el contrato de E-10) de modo que:

1. Una evaluación del tenant Gratis para `CI-DEMO-0001002` (titular sintético
   «moroso») queda en `REVISION_MANUAL`, como ya ocurre hoy, y aparece en
   `GET /revision-manual` para `ANALYST`, con `solicitanteId` y los productos activos.
2. `POST /revision-manual/{evaluacionId}/resolucion` acepta `ANALYST` y responde
   200 con `decision` y `justificacion` tal como se enviaron.
3. El caso resuelto deja de aparecer en la bandeja.
4. La resolución queda en `audit.access_log` con el `X-Correlation-Id` de esa
   petición, visible en Configuración › Seguridad para `TENANT_ADMIN`. El
   escenario busca la fila por correlation-id y no depende del nombre de la acción.

## Lo que necesita A-11 para que pase `plan-upgrade.spec.ts`

El cambio de plan ya es efectivo en ≤ 1 min sin volver a iniciar sesión:

- `PATCH /admin/tenants/{id}` (A-08) cambia el plan;
- las compuertas leen el plan del servidor en cada petición;
- el límite por minuto vuelve a leerlo cada minuto.

Esto se comprobó en local el 2026-10-09 con un operador temporal: las funciones
Pro aparecieron en la misma sesión a los pocos segundos. Lo que falta es el
**operador sembrado**:

1. En `seed/tenants.yaml`, un usuario `PLATFORM_ADMIN` dentro de un tenant (la
   API exige `tenant_id` también al operador). Se sugiere Fintech Vecina, para no
   compartir el límite por minuto del tenant Gratis que cambia de plan:

   ```yaml
         - key: operador
           username: operador.demo
           email: operador.demo@doggycredit.invalid
           nombre_completo: "Operación DoggyCredit (demo)"
           roles: [PLATFORM_ADMIN]
           password_env: DC_SEED_PLATFORM_ADMIN_PASSWORD
   ```

2. `DC_SEED_PLATFORM_ADMIN_PASSWORD` en `.env.example`. Para la demo en la VM,
   también en el `.env` del VPS.
3. Regenerar el realm (`python scripts/generate_realm.py`): el job 3 exige que
   esté al día.

El escenario toma al operador de `seed/tenants.yaml`, sin copiar usuarios, y
restaura el plan Gratis al final aunque falle.

## Cerrar E-12

E-12 queda lista cuando ambos interruptores están en `true` y el job 6 pasa sobre
`main` con los 6 escenarios.

Pendiente aparte (no lo exige A-11): la barra lateral consulta el plan una sola
vez por sesión. Tras el cambio sigue diciendo «Plan Gratis» hasta recargar,
aunque las funciones Pro ya estén habilitadas.
