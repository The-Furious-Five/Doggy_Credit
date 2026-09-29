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
