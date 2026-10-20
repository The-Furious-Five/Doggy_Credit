# E2E de los flujos del Sprint 4 (A-16 / LOS-136)

Cuatro escenarios nuevos en `e2e/tests/`, contra el Compose completo y sin mocks de
la API, como los de E-06 y E-12. Los que esperan una historia de otro integrante
quedan en `test.fixme` y el CI los informa como omitidos.

| Escenario | Archivo | Corre hoy | Espera | Interruptor |
| --- | --- | --- | --- | --- |
| Operador suspende y reactiva; el analista recibe 403 `TENANT_SUSPENDED` y vuelve a operar sin iniciar sesión | `tenant-suspension.spec.ts` | Sí (E-14 fusionada) | **B-13** (LOS-132) solo para el diálogo de confirmación | `vistaOperador` |
| Resolver un caso en revisión manual actualiza la tasa de override (Pro) | `portfolio-kpis.spec.ts` | No | **C-14** (LOS-130) y **D-13** (LOS-133) | `kpisCartera` |
| Analítica del plan Gratis invita a Pro en lugar de fallar | `portfolio-kpis.spec.ts` | Sí | — | — |
| Consumo +1 por evaluación; la misma `Idempotency-Key` no suma | `usage-idempotency.spec.ts` | Sí | — | — |
| Productos elegibles con criterios ✓/✗ e invitación a Pro (Gratis); ranking con monto y plazo (Pro) | `eligibility-recommendations.spec.ts` | Sí (E-16 y la vista de C-16, LOS-140) | — | `elegibilidad` (encendido) |

Los interruptores están en `e2e/tests/support.ts` (`merged`). **Quien fusiona la
historia pone su interruptor en `true` en el mismo PR** y desde ahí el escenario
corre en el job **6 · Flujo E2E** de cada PR y de `main`. Para probarlo antes en
la rama de la historia:

```bash
E2E_DEPENDENCIAS=kpisCartera npx playwright test tests/portfolio-kpis.spec.ts
```

## Aislamiento entre escenarios

- La suite corre en serie (`workers: 1`). La suspensión usa el tenant Gratis del
  analista; el operador vive en otro tenant y no se bloquea a sí mismo.
- `tenant-suspension.spec.ts` reactiva la institución en un `finally`, también si
  falla. `setTenantActive` no hace nada si el tenant ya está en ese estado.
- El consumo se mide sobre el tenant Pro (sin cuota mensual), así no gasta la
  cuota del Gratis que usan los demás escenarios, y compara diferencias, no
  valores absolutos: se puede repetir sobre la misma base.

## Lo que necesita cada historia para que su escenario pase

### B-13 (LOS-132): `vistaOperador`

- En Administración de tenants, el botón de cada fila se llama
  `Suspender institución` / `Reactivar institución` (hoy `Desactivar` / `Activar`;
  el escenario acepta ambos) y abre un `dialog` cuyo botón de confirmación empieza
  con `Suspender` o `Reactivar`.
- El analista suspendido ve un aviso con rol `status` o `alert` que contenga
  «suspendida». Hoy lo cubre el toast del problema RFC 9457 («Tenant suspendido»).

### C-14 (LOS-130) y D-13 (LOS-133): `kpisCartera`

- `GET /analytics/resumen` devuelve `pro.tasaOverride` con `overrides`,
  `evaluaciones` y `porcentaje` según el contrato de A-15 (LOS-128). Una resolución
  de `POST /revision-manual/{id}/resolucion` suma 1 a `overrides` del mes en curso.
- Analítica muestra un `ds-stat` con el texto «Tasa de override» que incluye la
  cantidad de overrides.

### E-16: `elegibilidad`

- `POST /credit-assessments` devuelve `elegibilidad` en todos los planes y, en
  Pro, `recomendaciones` con `plazoSugeridoMeses`.
- El resultado de Nueva evaluación muestra una `region` con nombre «Productos
  elegibles», con los criterios marcados `✓` y `✗`; en Gratis, el texto
  «disponible(s) en el plan Pro» o «… con Pro»; en Pro, la lista «Productos
  recomendados» con el monto (`Bs …`) y «N meses» en cada elemento.

Si la vista final usa otros textos, se ajusta el escenario en el mismo PR que
enciende el interruptor.
