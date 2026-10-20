# DoggyCredit para iPhone

App nativa SwiftUI para evaluar clientes, consultar su Perfil Financiero 360,
resolver revisiones y consultar analítica de la institución autenticada.

## Servidor

La app usa exclusivamente **https://doggycredit.los5furiosos.cloud**:

- API: `https://doggycredit.los5furiosos.cloud/api/v1`.
- Identidad: `https://doggycredit.los5furiosos.cloud/auth/realms/doggycredit`.
- Consola web: `https://doggycredit.los5furiosos.cloud/app`.

No hay selector de servidor, acceso de demostración, usuarios precargados ni
respuestas locales de respaldo. Las preferencias antiguas que apuntaban a otro
servidor y las sesiones de demostración se descartan al abrir esta versión.
Los listados vacíos y los errores reflejan la respuesta de la API.

**Los datos del servidor pueden ser sintéticos.** El backend de este proyecto
configura simuladores de buró, banco y billetera en `infra/compose.prod.yaml` y
publica `simulado` en las evaluaciones. La app conserva esa indicación y muestra
«Datos simulados» cuando corresponde. Una conexión HTTP real no sustituye esos
proveedores por entidades financieras reales. Eso requiere configurar los
proveedores y sus credenciales en el backend.

## Ejecutar

Requiere Xcode 27; objetivo mínimo iOS 18.

1. Abrir `ios-mobile-app.xcodeproj` y seleccionar el esquema `ios-mobile-app`.
2. Ejecutar en iPhone o simulador.
3. Pulsar **Iniciar sesión con tu institución** y acceder con una cuenta del
   servidor que tenga `tenant_id` y un rol asignado.

El cliente público `doggycredit-mobile` usa Authorization Code + PKCE S256.
El callback `doggycredit://oauth/callback` está registrado en
`Configuration/Info.plist`. La contraseña se introduce en Keycloak, dentro de
`ASWebAuthenticationSession`, y nunca se almacena en la app.

Antes de abrir una sesión nueva se comprueba que la API reconoce la institución.
La app comprueba issuer, audiencia `doggycredit-api` e institución del token para
no restaurar credenciales de otro entorno; la API valida la firma y los permisos.
Los tokens se guardan en el Llavero y se renuevan al vencer o después de un 401.
Las caídas temporales del servidor de identidad conservan la sesión para reintentar.
Las peticiones usan sesiones efímeras sin caché de disco.

## Flujos conectados

| Pantalla o acción | API v1 |
| --- | --- |
| Cuenta e institución | `GET /tenant/actual`, `/tenant/actual/uso`, `/tenant/actual/preferencias` |
| Clientes y búsqueda exacta | `GET /solicitantes` con paginación, segmento y cabecera `X-Documento` |
| Perfil del cliente | `GET /solicitantes/{id}` |
| Registrar solicitante | `POST /solicitantes` |
| Consentimientos | `GET /consentimientos`, `POST /consentimientos`, `POST /consentimientos/{id}/revocar` |
| Evaluaciones e historial | `GET /credit-assessments`, `GET /credit-assessments/{id}` |
| Evaluar | `POST /credit-assessments` con `Idempotency-Key` estable entre reintentos |
| Estado de fuentes | `GET /integraciones` |
| Revisión manual | `GET /revision-manual`, `POST /revision-manual/{id}/resolucion` |
| Analítica | `GET /analytics/resumen` |

La API determina los datos y los permisos de cada institución a partir del token.
No se envían identificadores de tenant escogidos por el usuario.
Los errores de institución, cuota y preferencias se muestran con reintento.
La administración de productos, políticas, integraciones y equipo se abre en la
consola web desde Cuenta.

## Arquitectura

- `App/`: sesión, navegación y bloqueo con Face ID / Touch ID / código.
- `Core/Auth/`: PKCE, OIDC, Llavero y renovación serializada.
- `Core/Networking/`: `LiveDoggyCreditService`, HTTP y errores de la API.
- `Core/Models/`: modelos del contrato OpenAPI v1.
- `Features/`: pantallas y modelos observables.
- `DesignSystem/`: marca, componentes nativos y Liquid Glass según la versión de iOS.
- `ios-mobile-appTests/TestSupport/`: datos sintéticos e interceptores HTTP,
  compilados **solo en las pruebas**, fuera de la aplicación.

## Pruebas

### Resiliencia y componentes de estado

`DesignSystem/Components/LoadState.swift` incluye los componentes nativos
`RefreshStatusView` (actualización, antigüedad de los datos y reintento) y
`PaginationFooter` (carga adicional y recuperación de errores). Ambos reutilizan
`NoticeCard`, estilos semánticos y controles de SwiftUI. Clientes y Revisión
conservan el contenido ante errores transitorios y muestran el reintento; un 403
de actualización retira el contenido conservado. Los filtros de Revisión solo
declaran un resultado vacío definitivo cuando no quedan páginas pendientes.

Al elegir otro cliente con un borrador abierto, la app permite continuar el
actual, descartar y empezar otro, o cancelar. La reevaluación de una ficha
conocida presenta sus datos registrados en solo lectura. Si el documento se
reconoce durante el alta, se muestra la ficha antes de continuar: actividad e
ingreso escritos no sustituyen los datos del servidor. La API móvil actual no
incluye actualización de solicitantes.

`HardeningTests` cubre conflictos de borrador, reevaluación, paginación filtrada,
reintentos, pérdida de permisos y respuestas antiguas de búsqueda o segmento.
`HardeningScreensTests` adjunta capturas de las vistas reales con datos sintéticos
del target de pruebas, en claro y oscuro con texto de accesibilidad. Incluye
orientación horizontal en iPhone; la rotación física de iPad se comprueba mediante
`ScreenTourUITests`. No modifica
el acceso institucional ni incorpora demostraciones a la app distribuida.

### Acabado y texto accesible

La bienvenida integra el acceso en el contenido de iPad y en tamaños de
accesibilidad. Con texto accesible, «Servidor» despliega el dominio bajo demanda.
El progreso de evaluación reutiliza `AdaptiveStack`; los requisitos pendientes
forman parte del formulario desplazable y del nombre de ayuda de la acción.
`RowHeadline` permite que la fecha ocupe varias líneas con tamaños de accesibilidad.
`PaginationFooter` usa el tamaño grande del control nativo.
Las razones del score colocan los puntos debajo de la explicación con texto
accesible, conservando el ancho de lectura.

El conflicto entre borradores utiliza una hoja nativa con titulares protegidos,
acciones desplazables y cancelación. La evaluación se abre al terminar de cerrar
esa hoja. En el resultado, «Copiar identificador» copia la correlación completa,
también mediante una acción de VoiceOver.

Evidencia y validación del acabado: [polish del 19 de octubre](docs/polish-2026-10-19.md).

### Ejecutar

```bash
xcodebuild test -project ios-mobile-app.xcodeproj -scheme ios-mobile-app \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

Incluyen decodificación del contrato, evaluación, consentimiento, búsqueda,
resolución, migración de sesiones antiguas y transporte HTTP. Las pruebas de
interfaz comprueban que no se puede omitir la autenticación con los argumentos
antiguos de demostración y abren el formulario real de Keycloak sin crear datos.

Las comprobaciones opcionales del servidor son de solo lectura:

```bash
TEST_RUNNER_DC_LIVE_TESTS=1 xcodebuild test \
  -project ios-mobile-app.xcodeproj -scheme ios-mobile-app \
  -destination 'platform=iOS Simulator,name=iPhone 17' \
  -only-testing:ios-mobile-appTests/LiveServerTests
```

Sin credenciales, verifican descubrimiento OIDC, formulario PKCE y rechazo de
peticiones sin autenticación. Para comprobar además los listados y detalles de
una cuenta real, proporcionar temporalmente un access token válido mediante
`TEST_RUNNER_DC_LIVE_ACCESS_TOKEN`, sin guardarlo en archivos ni en el repositorio.
La prueba autenticada se omite explícitamente cuando no se proporciona ese token.

Los argumentos DEBUG de navegación (`-DCTab`, `-DCOpen`, `-DCDocument`) necesitan
una sesión real. `-DCSignedOut YES` y `-DCAppearance light|dark` sirven para probar
la bienvenida. Ningún argumento genera solicitantes, consentimientos o evaluaciones.
