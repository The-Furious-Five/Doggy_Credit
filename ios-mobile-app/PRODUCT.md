# Product — DoggyCredit iOS

<!-- impeccable:product-schema 1 -->

Ámbito: exclusivamente `ios-mobile-app/`. Contexto confirmado por el usuario
durante `impeccable init`; las capacidades y restricciones siguientes proceden
de la documentación y la implementación de esta app.

## Platform

ios

## Users

- Analistas de instituciones financieras que evalúan clientes en campo,
  consultan sus antecedentes y resuelven casos entre visitas.
- Administradores de la institución que consultan clientes, revisiones,
  analítica, uso del plan y estado de su cuenta según sus permisos.
- Los solicitantes evaluados pertenecen a los segmentos persona, emprendedor
  y pyme. Sus datos y autorizaciones requieren protección durante la atención.

## Product Purpose

Llevar la operación crediticia de DoggyCredit al dispositivo móvil: registrar
al solicitante, obtener su consentimiento, consultar las fuentes autorizadas,
entender el resultado y resolver las revisiones que requieren criterio humano.

El éxito consiste en completar esas tareas con información comprensible,
trazabilidad y respeto por los permisos de la institución, conservando el
contexto ante interrupciones y haciendo explícitas las limitaciones de los datos.

## Positioning

Cliente móvil de la plataforma SaaS multi-tenant de inteligencia crediticia
DoggyCredit. Su mecanismo reúne consentimiento, Perfil Financiero 360 con
información de buró, banco y billetera, score con razones y revisión humana
justificada en un mismo flujo institucional.

La preaprobación y la revisión dependen de las políticas de la institución.
Un score aislado no representa una aprobación de crédito ni garantiza un resultado.

## Operating Context

- App existente en SwiftUI y Swift 6, con objetivo mínimo iOS 18. El proyecto
  configura iPhone e iPad; su README indica Xcode 27 como requisito de ejecución.
- Áreas principales: Inicio, Revisión, Clientes, Analítica y Buscar; Cuenta
  contiene institución, uso del plan, privacidad y acceso a la consola web.
- La evaluación sigue cuatro pasos: Solicitante → Consentimiento → Fuentes →
  Resultado. El borrador vive en la sesión y puede retomarse al cerrar la hoja.
  Cerrar sesión lo descarta; los registros ya enviados permanecen en el servidor.
- La app requiere conectividad con el servidor configurado en
  `https://doggycredit.los5furiosos.cloud`. Los datos y permisos proceden de su
  API v1 y de la identidad institucional gestionada en Keycloak.
- Productos, políticas, integraciones, claves de API, webhooks y equipo se
  administran en la consola web, accesible desde Cuenta.
- Textos en español y formato regional de Bolivia (`es_BO`). Las solicitudes
  de crédito se expresan en bolivianos; los modelos también contemplan USD.

## Capabilities and Constraints

- Buscar solicitantes por documento exacto, filtrar y paginar clientes,
  registrar solicitantes y consultar su Perfil Financiero 360 e historial.
- Registrar consentimientos por fuente y permitir su revocación. Ninguna
  autorización se preselecciona: buró es obligatorio para continuar la
  evaluación; banco y billetera son opcionales.
- Consultar el estado de las fuentes y presentar score, razones, decisión y
  resultados parciales según la respuesta de la API. Un dato ausente se
  representa como desconocido; no debe sustituirse por una cifra inventada.
- Conservar una clave de idempotencia entre reintentos de una evaluación para
  evitar duplicados. Los fallos deben permitir reintentar conservando el contexto.
- Resolver revisiones con decisión y justificación obligatorias; aprobar
  requiere seleccionar un producto. Respetar los permisos y los conflictos por
  casos ya resueltos o productos que dejaron de estar disponibles.
- El modelo de decisiones no contempla rechazo automático: el rechazo se
  decide en una revisión humana. Conservar esa distinción frente a preaprobación,
  revisión manual y solicitud de datos.
- Consultar analítica y uso del plan según lo permitido por la API. Las cuotas
  y las capacidades del plan se determinan en el servidor.
- Acceso institucional mediante Authorization Code + PKCE S256 y
  `ASWebAuthenticationSession`. Las credenciales se introducen en Keycloak;
  los tokens se guardan en el Llavero. La API valida firma y permisos.
- La institución procede del token. Mantener el aislamiento entre instituciones
  y el enmascarado de documentos y montos según preferencias y rol; mientras se
  desconocen las preferencias, la app enmascara los datos.
- Bloqueo local opcional con Face ID, Touch ID o código del dispositivo y
  protección del contenido en el selector de apps.
- La aplicación usa el servidor configurado, sin selector de entorno, acceso
  de demostración, usuarios precargados ni respuestas locales de respaldo.
  Los datos sintéticos de pruebas se compilan únicamente en los targets de tests.
- Una conexión real al servidor puede devolver datos sintéticos de sus
  simuladores. Mantener «Datos simulados» cuando corresponda. La presencia de
  una integración no demuestra conexión con una entidad financiera real.
- Reutilizar el sistema de diseño nativo existente y respetar las reglas del
  repositorio; este registro no establece una nueva dirección visual.

## Brand Commitments

- Conservar el nombre DoggyCredit y los activos oficiales existentes en
  `ios-mobile-app/Assets.xcassets/`, incluidos el emblema y los iconos de la app.
- Mantener la identidad compartida documentada en
  `../docs/design-system/README.md`, con implementación nativa en
  `ios-mobile-app/DesignSystem/`.
- Hablar de forma directa, en español, sin culpar al usuario. Los errores deben
  explicar qué ocurrió, qué se conserva y cómo continuar.
- No prometer aprobación ni presentar datos simulados como información
  financiera verificada.

## Evidence on Hand

- `README.md`: alcance móvil, servidor, autenticación, flujos conectados,
  arquitectura y comandos de pruebas.
- `ios-mobile-app/Features/` y `ios-mobile-app/App/`: flujos implementados,
  navegación, sesión y bloqueo.
- `ios-mobile-app/Core/`: modelos del contrato, identidad, acceso a la API y
  política de privacidad.
- `ios-mobile-app/Assets.xcassets/` y `ios-mobile-app/DesignSystem/`: activos
  oficiales y sistema nativo existente.
- `ios-mobile-appTests/` y `ios-mobile-appUITests/`: pruebas de contrato,
  dominio, flujos, autenticación, transporte y acceso a la interfaz. Los fixtures
  de `ios-mobile-appTests/TestSupport/` son evidencia sintética para pruebas.
- `../contracts/openapi/doggycredit-api.v1.yaml`: contrato compartido de la API.
- `../docs/debt-register.md`: limitaciones registradas sobre fuentes simuladas,
  scoring entrenado con datos sintéticos y validez legal del consentimiento
  pendiente de verificar. No afirmar validación real o legal a partir del prototipo.

## Product Principles

1. El consentimiento y la privacidad preceden a la consulta de datos financieros.
2. Cada resultado se entiende por sus razones, fuentes, estado y limitaciones.
3. La institución y los permisos del servidor gobiernan toda operación.
4. Las interrupciones y los reintentos conservan el contexto sin duplicar acciones.
5. La experiencia respeta los comportamientos nativos de iOS y permite trabajar
   con claridad durante la atención en campo.

## Accessibility & Inclusion

- Preservar etiquetas y estados accesibles, compatibilidad con VoiceOver,
  Dynamic Type y preferencias de movimiento reducido presentes en el código.
- Conservar los temas, la legibilidad y la adaptación a tamaños de texto
  accesibles; no comunicar estados únicamente mediante color.
- Mantener términos comprensibles, navegación y controles nativos, y explicar
  los permisos, la ausencia de datos y el siguiente paso ante un error.
- Este contexto no certifica una auditoría completa de accesibilidad.

## Open Decisions

- No se han establecido metas numéricas de éxito ni un estándar adicional de
  accesibilidad específico para esta app.
- Las futuras capacidades sin conexión y las nuevas integraciones reales
  requieren una definición propia; el contexto registra el funcionamiento actual.
