# DoggyCredit Web

SPA Angular 22 con login OIDC Authorization Code + PKCE contra Keycloak.
Paleta y logo originales: verde #10403C / #1A5149, dorado #D59132 / #F7DDA0,
marfil #FFFEFD. Diseño adaptable, estados de carga/error y aviso Datos simulados.

```bash
npm ci
npm test -- --watch=false
npm run build
```

Usar Compose desde `infra/` y abrir http://localhost:8080 para el login integrado. La SPA deriva authority
y API del origen; un `ng serve` aislado no incluye el proxy de Keycloak.
El código en `src/app/api` se genera desde OpenAPI con
`bash ../scripts/generate_clients.sh` y no se modifica manualmente.

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
