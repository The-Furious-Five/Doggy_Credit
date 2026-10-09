# Avisos al 80 % y al 100 % de la cuota (E-11 / LOS-123)

RF-PLN-06. La consola avisa cuando el tenant usa el 80 % y el 100 % de sus
evaluaciones del mes. El dato sale del contador existente,
`GET /tenant/actual/uso` (`evaluacionesMes` y `limiteMensual`; `0` = sin límite).
No hay cambios en la API ni en el contrato.

## Comportamiento

| Uso del mes | Aviso en la consola |
| --- | --- |
| Cargando, error o plan sin límite | Ninguno. La barra lateral ya muestra «Cargando uso mensual…» o «Uso mensual no disponible» |
| Menos del 80 % (incluye 0) | Ninguno |
| Desde el 80 % | `ds-alert` de advertencia (`role=status`): «Usaste el N % de las evaluaciones del mes» |
| 100 % o más | `ds-alert` de peligro (`role=alert`): «Llegaste al límite…», las evaluaciones nuevas se pausan hasta el próximo mes |

- El aviso está arriba de todas las vistas de `/app/*` (`dc-quota-notice` en el
  shell). Lo ven todos los roles. `TENANT_ADMIN` tiene «Ver tu plan»
  (`/app/configuracion#plan`); quien no puede abrir Configuración ve «Avisa a
  quien administra tu institución para revisar el plan».
- **Al cruzar un umbral con la consola abierta** (por ejemplo, al evaluar),
  además aparece un toast, que se anuncia aunque el aviso quede fuera de
  pantalla. Entrar a la consola con el umbral ya superado no repite el toast.
- «Ocultar aviso» lo oculta durante la sesión del navegador (`sessionStorage`,
  por tenant y umbral) y devuelve el foco al contenido principal. Al pasar del
  80 % al 100 %, el aviso nuevo aparece aunque se haya ocultado el anterior.
- El mes se cuenta en UTC en el backend (`UsageMeterImpl`). Por eso los textos
  dicen «hasta el próximo mes», sin una fecha que podría no coincidir.

`TenantUsageService` comparte una sola consulta entre la barra, Configuración ›
Plan y el aviso. Nueva evaluación la refresca después de evaluar y ante
`QUOTA_EXCEEDED`. Configuración › Plan la refresca al abrirse.

## Probar a mano

Con el Compose del E2E (ver `e2e/README.md`), llevar el contador del tenant
Gratis de demo cerca del umbral y evaluar desde la consola:

```bash
docker exec doggycredit-e2e-postgres-1 psql -U doggy_app -d doggycredit -c "
  insert into billing.monthly_usage (tenant_id, year_month, evaluaciones)
  values ('9c7e7c1a-0000-4000-8000-000000000001', to_char(now() at time zone 'utc', 'YYYY-MM'), 399)
  on conflict (tenant_id, year_month) do update set evaluaciones = excluded.evaluaciones"
```

Con 399, una evaluación lleva el contador a 400 y aparecen el aviso y el toast
del 80 %. Con 499, una evaluación lleva a 500 y aparece el aviso del límite;
la siguiente evaluación responde `QUOTA_EXCEEDED`. Al terminar, borrar la
fila: el E2E del tenant Gratis falla con la cuota agotada.

## Verificación

`tenant-usage.service.spec.ts` cubre los umbrales (80 % en enteros, límite 0 y
consumo por encima del límite) y la recarga. `quota-notice.spec.ts` es la prueba
de componente: carga, error, vacío, cada umbral, rol, toast al cruzar y ocultar.
Además se revisó con axe (WCAG 2.1 AA) en tema claro y oscuro, sin violaciones.
