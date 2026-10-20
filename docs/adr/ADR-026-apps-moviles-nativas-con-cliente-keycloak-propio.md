# ADR-026 · Apps móviles nativas con su propio cliente de Keycloak

Fecha: 2026-10-20. Estado: aceptado (construido).

## Contexto

Hay una app SwiftUI (`ios-mobile-app/`, iOS 18+) y una app Kotlin con Jetpack
Compose (`android-mobile-app/`, API 26+). Ambas consumen la misma API pública y el
mismo realm que la SPA web.

## Decisión

Ambas son nativas y usan el cliente público `doggycredit-mobile`
(`infra/keycloak/doggycredit-realm.json` y `doggycredit-mobile.json`): flujo
estándar con PKCE S256, sin implícito ni acceso directo con contraseña, y
redirección `doggycredit://oauth/callback`. Es un cliente distinto de
`doggycredit-web`, con sus propias redirecciones y su mapper `tenant_id`. Apuntan
solo a `https://doggycredit.los5furiosos.cloud` (`/api/v1`,
`/auth/realms/doggycredit`); no hay servidor seleccionable, modo demo ni datos
locales. No usan WebView.

## Alternativas consideradas

- Reutilizar `doggycredit-web`: descartado; sus redirecciones y orígenes web no sirven al esquema nativo.
- WebView o app híbrida: descartado; peor experiencia y seguridad del login.
- Una API específica para móvil: descartado; los mismos contratos y roles bastan.

## Consecuencias

Endurecer o revocar el acceso móvil no afecta a la web. Cambiar el dominio exige
recompilar las apps. Los datos de los simuladores llegan marcados como `simulado`
y las apps lo muestran («Datos simulados»).
