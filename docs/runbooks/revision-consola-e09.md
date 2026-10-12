# Consola sin datos simulados (E-09 / LOS-107)

Desde E-09 ninguna vista de `/app/*` muestra cifras, planes o funciones que no
vengan de la API. `web/src/app/data.ts` ya no existe: la landing conserva sus
textos de marketing (productos y tabla de precios) en `landing/landing.ts`.

## Qué se retiró y qué lo reemplaza

| Vista | Antes | Ahora |
| --- | --- | --- |
| Barra de la consola | Badge «Datos simulados» siempre visible | Sin badge. «Datos simulados» solo acompaña un resultado con `simulado=true` en Nueva evaluación |
| Configuración › Plan | Tarjetas de precios de `data.ts` y «Mejorar a Pro» que solo mostraba el toast «Te contactaremos» | Plan actual (`GET /tenant/actual`) y cuota (`/tenant/actual/uso`), con carga y error. El cambio de plan lo hace el equipo de DoggyCredit desde Administración de tenants (DEBT-010); «Comparar planes» abre `/#planes` |
| Desarrolladores | «Uso de la API» fijo en 18 de 60 y «Límite del plan Gratis: 60 por minuto» (el real es 30) | Sin barra: la API no publica el consumo por minuto. Se explica el 429 con `Retry-After` |
| Productos | Badge «Productos propios en plan Pro» (la API no permite crear productos) | Sin badge |
| Perfil del cliente | Pestaña «Recomendaciones» siempre vacía, con «aparecerán aquí después de evaluar» | Sin pestaña: la API no expone las recomendaciones por evaluación |
| Nueva evaluación | «Las recomendaciones de productos aparecerán aquí cuando estén disponibles» | Sin texto. Al llegar a la cuota, el enlace «Ver tu plan» solo se ofrece a `TENANT_ADMIN` |

Las recomendaciones por evaluación siguen guardándose para la analítica
(`AssessmentAnalyticsSnapshot`), pero `Evaluacion` no las incluye en el contrato.
Mostrarlas en el perfil o en el resultado requiere ampliar primero el OpenAPI.

## Revisión de teclado, nombres accesibles y temas

Se revisaron todas las vistas de `/app/*` y la landing con axe-core 4.10
(WCAG 2.1 AA). Además, se recorrieron con Tab en tema claro y oscuro, como
admin Gratis, admin Pro y analista, contra el Compose del E2E. Correcciones:

- **Nueva evaluación:** al cambiar de paso, el foco pasa al título del paso
  nuevo. Antes caía en `body` después de «Evaluar» o «Atrás», porque el botón
  pulsado desaparecía.
- **Productos y Políticas:** cada interruptor se nombra con su producto o regla
  («Activo: Microcrédito», «Pausada: Datos insuficientes»), no solo con su estado.
- **Desarrolladores:** el ejemplo `curl` desplazable se alcanza con Tab
  (`scrollable-region-focusable`).
- **Webhooks en plan Gratis:** el 403 `FEATURE_NOT_IN_PLAN` ya no abre un toast
  de error al entrar, porque la tarjeta ya explica el plan. La vista declara los
  códigos que presenta con `HANDLED_PROBLEMS`, y el interceptor sigue avisando
  los demás errores.
- **Configuración:** si fallan `GET /tenant/actual` o las preferencias, se
  muestra «No disponible» o un aviso, sin romper la vista.

Resultado: no quedan violaciones de axe. Lo único que axe informa es el
contraste de «Credit» en el logotipo en tema claro, que WCAG 1.4.3 exime por
ser parte del logo. Los campos numéricos dibujan el anillo de foco en su
contenedor (`:has(:focus-visible)`), en ambos temas.

## Verificación

Desde `web/`: `npm run ds:check`, `npm run ds:test`, `npm test -- --watch=false`
y `npm run build`. El criterio de la historia se comprueba con:

```bash
grep -r "data'" web/src/app/console   # sin resultados
```

El E2E (`e2e/`, ver su README) recorre el perfil con las pestañas Resumen, Score
y Fuentes.
