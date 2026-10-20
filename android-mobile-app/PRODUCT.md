# Product

<!-- impeccable:product-schema 1 -->

Contexto de producto para `android-mobile-app`, elaborado a partir del código y
la documentación del repositorio. El usuario confirmó el alcance exclusivo de
Android y el objetivo de preparar un piloto con una institución. Este archivo
no define el contexto de las aplicaciones web o iOS.

## Platform

android

## Users

El público previsto en la documentación de DoggyCredit son analistas y
administradores de instituciones microfinancieras, cooperativas y fintech
bolivianas. En Android realizan evaluaciones, consultan clientes y resuelven
casos con la cuenta y los permisos de su institución.

La aplicación presenta la evaluación en campo como un caso de uso. Los
solicitantes son las personas o empresas evaluadas; el acceso implementado es
institucional, sin un flujo de autoservicio para solicitantes.

La institución participante, los usuarios concretos y las condiciones de trabajo
del piloto están pendientes de definición.

## Product Purpose

Llevar el flujo de evaluación crediticia de DoggyCredit a una aplicación Android
nativa: identificar al solicitante, registrar consentimiento por fuente,
consultar el Perfil Financiero 360, obtener un score con razones y resolver los
casos que requieren revisión manual.

La prioridad confirmada es preparar un piloto institucional. El recorrido que
debe poder evaluarse es una operación completa con autenticación institucional,
consentimiento explícito, resultados trazables y recuperación ante errores. Los
criterios medibles de aceptación del piloto aún no están acordados.

## Positioning

Android utiliza las cuentas, API y reglas de negocio de DoggyCredit mediante
una experiencia nativa en Kotlin y Jetpack Compose. El producto consolida
información de buró, banco y billetera en un perfil financiero y ofrece un score
explicable junto con información para la revisión y selección de productos.

Esta es la propuesta implementada y documentada; no establece una exclusividad
comercial ni demuestra superioridad frente a otros productos.

## Operating Context

- Aplicación nativa con Material 3. Compatible desde Android 8.0/API 26;
  `compileSdk` y `targetSdk` actuales: 36.
- Navegación principal: Inicio, Revisión, Clientes, Analítica y Buscar. Cuenta
  reúne identidad, institución, consumo y opciones de seguridad.
- La evaluación tiene cuatro etapas: Solicitante, Consentimiento, Fuentes y
  Resultado. Se puede retomar su borrador durante la sesión y tras una rotación;
  al terminar el proceso o cerrar sesión se descarta.
- La API y Keycloak se configuran en `Server`, dentro de
  `app/src/main/java/com/doggycredit/app/data/Api.kt`, con el origen
  `https://doggycredit.los5furiosos.cloud`. Es la configuración del código, no una
  comprobación de disponibilidad del servidor realizada durante este init.
- La operación requiere conexión al servidor. No hay modo sin conexión, WebView,
  acceso de demostración ni respuestas locales para sustituir fallos de la API.
- La administración de productos, políticas, integraciones y equipo se deriva a
  la consola web desde la aplicación.
- Idioma y formatos actuales: español de Bolivia (`es-BO`), montos con su moneda
  y períodos de analítica en `America/La_Paz`.

## Capabilities and Constraints

- Consultar clientes paginados, filtrar por segmento, buscar exactamente por CI
  o NIT y abrir su perfil, fuentes, historial y consentimientos. La búsqueda de
  documento usa `X-Documento`, sin incluirlo en la URL.
- Registrar o reutilizar solicitantes y evaluar con consentimiento por fuente.
  Ninguna autorización empieza seleccionada; buró es obligatorio y banco y
  billetera son opcionales en el flujo actual.
- Presentar score, razones, estado, versión y correlación devueltos por la API.
  La app no calcula un score alternativo ni inventa valores faltantes.
- Resolver casos de revisión manual con producto al aprobar, justificación y
  manejo de cambios concurrentes. La API conserva la autoridad sobre permisos
  y resultados de negocio.
- Consultar analítica por período, consumo y cuota del plan. El bloque avanzado
  depende de lo que habilite la API; la aplicación no efectúa contratación ni
  facturación de planes.
- Conservar borradores durante la sesión y claves de idempotencia al reintentar
  una evaluación sin cambiar su petición. Mostrar fallos con una acción de
  recuperación y el código de seguimiento cuando esté disponible.
- Autenticar con OIDC Authorization Code + PKCE S256 mediante AppAuth y Custom
  Tabs. Las credenciales se introducen en Keycloak. El tenant procede del token;
  el usuario no escoge un identificador de institución para las peticiones.
- Proteger tokens con Android Keystore, mantener el enmascarado según rol y
  preferencias, y conservar la portada de privacidad, `FLAG_SECURE` y el bloqueo
  opcional por biometría o credencial del dispositivo.
- Mantener los datos de prueba fuera del APK de la aplicación. La conexión a
  una API desplegada no convierte sus fuentes financieras simuladas en reales;
  el indicador «Datos simulados» debe conservarse cuando corresponda.

### Decisiones pendientes para el piloto

- Institución, participantes, dispositivos, conectividad y criterios de éxito.
- Si el piloto usará datos sintéticos o datos personales reales, y qué fuentes
  participarán. El registro de deuda compartido documenta fuentes simuladas,
  scoring entrenado con datos sintéticos y consentimiento sin validez legal
  verificada (DEBT-004 y DEBT-005).
- Resolución por los responsables de plataforma de las dependencias del piloto
  registradas en `../docs/debt-register.md`: disponibilidad y respaldos
  (DEBT-001), gestión de claves antes de datos personales reales (DEBT-006) y
  ancla externa de auditoría (DEBT-009). El init de Android no resuelve ni
  certifica esas dependencias.
- Firma y distribución de la app para los participantes. El repositorio
  documenta Release sin firma de distribución; el certificado del propietario
  se configura fuera del repositorio.

## Brand Commitments

Conservar el nombre DoggyCredit y los activos oficiales derivados de
`../../DoggyCredit-brand/`. La interfaz existente tiene componentes comunes en
`app/src/main/java/com/doggycredit/app/ui/components/` y fundamentos en
`app/src/main/java/com/doggycredit/app/ui/theme/`.

Mantener terminología coherente: institución, solicitante, Perfil Financiero
360, consentimiento, evaluación, revisión manual y datos simulados. El contenido
debe distinguir resultados de la API, ejemplos y funciones pendientes, sin
presentar logos de terceros como convenios ni simulaciones como evidencia real.

## Evidence on Hand

- `README.md`: alcance nativo, acceso, funcionalidades y ejecución de Android.
- `docs/ios-parity.md`: matriz de flujos y contratos que Android consume.
- `docs/interface.md`: interfaz existente y comprobaciones visuales documentadas.
- `docs/validation.md`: validación previa y sus límites; no equivale a una
  validación nueva ni a una certificación del piloto.
- `app/src/main/`: implementación de sesión, API, modelos y pantallas.
- `app/src/test/` y `app/src/androidTest/`: pruebas y fixtures aisladas de la app.
- `../contracts/openapi/doggycredit-api.v1.yaml` y
  `../contracts/errors/catalog.yaml`: contratos compartidos de API y errores.
- `../docs/presentacion-final/guion.md` y `../docs/debt-register.md`: público,
  propuesta del producto y límites de la plataforma que afectan al piloto.
- `../../DoggyCredit-brand/` y `app/src/main/res/`: activos de marca y recursos.

No se aportaron resultados del piloto, testimonios de instituciones ni evidencia
de convenios con fuentes financieras reales. No deben inventarse.

## Product Principles

1. Consentimiento y permisos antes de consultar o mostrar información sensible.
2. Cada resultado debe conservar explicación, procedencia y trazabilidad.
3. Recuperar el trabajo ante errores sin duplicar evaluaciones ni ocultar fallos.
4. Mostrar con honestidad datos ausentes, fuentes simuladas y límites del plan.
5. Mantener una experiencia Android nativa y comprobar el recorrido institucional
   completo al preparar el piloto.

## Accessibility & Inclusion

Conservar semántica para TalkBack, descripción textual del score y los gráficos,
etiquetas para acciones, tamaños táctiles nativos y estados comprensibles sin
depender solo del color. Respetar tamaño de texto, tema del sistema y navegación
Atrás, con acceso al contenido tanto en teléfono como en pantallas amplias.

La documentación registra comprobaciones previas en tema oscuro, texto ampliado
y navegación adaptable. Las necesidades concretas de accesibilidad de los
participantes y la validación asistida del piloto siguen pendientes.
