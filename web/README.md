# DoggyCredit Web

SPA Angular 22 con login OIDC Authorization Code + PKCE contra Keycloak.
Paleta tomada del logo, en colores sólidos: pino #10403C, bosque #1A5149,
salvia #377766, miel #D59132, arena #F7DDA0, marfil #FFFEFD. Sin degradados.
Diseño adaptable y estados de carga/error. El aviso "Datos simulados" se muestra
solo en pantallas que presenten respuestas del simulador (`X-Simulated: true`),
no en el acceso.

```bash
npm ci
npm test -- --watch=false
npm run build
```

Levantar Compose completo y abrir https://localhost para el login integrado. La SPA deriva authority
y API del origen; un `ng serve` aislado no incluye el proxy de Keycloak.
El código en `src/app/api` se genera desde OpenAPI con
`npm run generate:api` y no se modifica manualmente.

[Arranque, certificados, pruebas y despliegue](../docs/runbooks/franco-sprint1.md).

## Autenticación y conexión de la consola

`src/app/auth` concentra la sesión OIDC, los guards y la política compartida
por las rutas y el menú. `/app` comprueba la sesión tanto al entrar como al
navegar entre sus vistas. Los permisos se leen de `realm_access.roles` del
access token validado por la biblioteca OIDC; no se toman de datos locales
ni de roles presentes solo en UserInfo.

| Rutas                                                                            | Acceso             |
| -------------------------------------------------------------------------------- | ------------------ |
| `/app/politicas`, `/app/productos`, `/app/configuracion`, `/app/desarrolladores` | `TENANT_ADMIN`     |
| `/app/admin/tenants`                                                             | `PLATFORM_ADMIN`   |
| Las demás vistas de `/app`                                                       | Sesión autenticada |

`PLATFORM_ADMIN` por sí solo no concede los permisos de `TENANT_ADMIN`. Una
sesión ausente o inválida vuelve a `/acceso`; un rol insuficiente vuelve a
`/app/resumen` con una notificación. El menú, el acceso a perfil y el enlace
para mejorar el plan respetan la misma política. La API mantiene su propia
validación de autenticación, roles y tenant.

`src/app/http` contiene los interceptores registrados globalmente. El JWT
vigente se añade únicamente a peticiones cuyo origen y ruta pertenecen a
`BASE_PATH`, también si usan URLs relativas. Las peticiones a Keycloak,
archivos estáticos u otros orígenes no reciben el token de la API.

Los errores HTTP de la API se muestran mediante `DsToastService`: los
Problem Details RFC 9457 conservan `title`, `detail` y el `correlationId`
visible. Si falta la extensión se usa `X-Correlation-Id`; los fallos de red
y respuestas incompletas tienen un mensaje alternativo. El error original
sigue llegando a la vista para mantener sus estados y opciones de reintento.

La barra A-01 muestra la identidad de OIDC y el nombre de la institución
obtenido con `TenantService.obtenerTenantActual()`. Un fallo de carga no se
presenta como un plan gratuito. «Cerrar sesión» utiliza `logoff()` para
terminar la sesión en Keycloak y volver al origen configurado, con protección
contra clics duplicados y posibilidad de reintento ante fallos.

Las pruebas de `app.routes.spec.ts` conservan los guards y políticas de las
rutas de producción y sustituyen las vistas para aislar las peticiones de
negocio. Cubren acceso directo, navegación con sesión expirada, permisos y
rutas públicas. Las pruebas de sesión, consola e interceptores comprueban
renovación de roles y JWT, aislamiento de credenciales, errores y logout.

## Design System obligatorio

Catálogo interactivo: `/design-system` (no requiere Keycloak). Ejecutar `npm start`
y abrir `http://localhost:4200/design-system` para desarrollar el catálogo.
El login integrado sigue requiriendo todos los servicios de Compose.

Toda pantalla utiliza `src/app/design-system/index.ts` y sus tokens. El build
comprueba automáticamente su uso. Antes de contribuir, leer el
[contrato de diseño](../docs/design-system/README.md).

```bash
npm run ds:check
npm run ds:test
```
