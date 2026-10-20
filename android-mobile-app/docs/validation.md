# Validación del port Android

Fecha: 14 de octubre de 2026.

## Cierre de auditoría — 19 de octubre de 2026

Se aplicaron todas las correcciones de la segunda auditoría y el pulido final.
La [revisión 3](audit-2026-10-19-revision-3.md) obtiene **19/20**, sin hallazgos
confirmados abiertos. El punto de adaptación restante requiere comprobaciones
específicas de multiventana y posturas plegables.

- 47 pruebas unitarias aprobadas. Incluyen contraste sobre marca y paginación
  del historial con error, reintento, deduplicación y respuestas tardías.
- Seis tamaños emulados, teléfono y tablet en ambas orientaciones, texto al
  140 % y 200 %, ambos temas y animaciones desactivadas en tablet horizontal.
  Se registraron 91 ejecuciones tras la implementación: 89 satisfactorias y
  dos fallos iniciales del recorrido de prueba ya corregidos y comprobados.
- La prueba integrada confirma Atrás con Inicio, Cuenta, perfil y cajón abierto;
  el bloqueo conserva ruta, borrador, foco seguro y ocultación semántica.
- Con 1.000 evaluaciones cargadas, el perfil pasa de 1.000 a 7 nodos de estado
  de tarjeta compuestos en la ventana inspeccionada. No es una medición de FPS.
- El contraste configurado de texto/progreso ocupado sobre marca es 8,997:1.
  Debug, instrumentación y Release compilan; Lint mantiene cero errores y
  41 avisos previos. Los tres controles web exigidos por `AGENTS.md` pasan.

La [evidencia y las huellas de esta versión](audit-evidence/2026-10-19/revision-3/verification-summary.json)
conservan las rondas completas, incluidos los dos fallos de la prueba al asumir
que una fila o encabezado fuera de pantalla seguían compuestos. La repetición
corregida en horizontal pasó. No se cambiaron datos financieros ni el archivo
real de sesión. El almacenamiento de prueba se aisló en su propia caché.

Los apartados siguientes son registros de etapas anteriores. Sus resultados
no se suman como nuevas pruebas de este cierre.

La refactorización visual posterior y sus comprobaciones específicas están
documentadas en [interface.md](interface.md), incluida la corrección de la franja
debajo del botón flotante.

## Refuerzo de flujos — 19 de octubre de 2026

- 42 pruebas unitarias aprobadas, sin errores ni omisiones. Incluyen conflicto
  entre borradores y clientes, reinicio tras completar, bloqueo durante una
  operación, cierre de sesión, validación por campo, respuestas tardías,
  paginación duplicada y recuperación tras un error de conexión.
- 16 escenarios de interfaz comprobados en el Pixel 9 Pro emulado, API 37:
  13 de `AuthenticatedFlowTests` y tres pruebas de `NativeUiTests` para
  validación, consentimiento y score accesible. La primera ejecución aprobó
  15; el recorrido de evaluación se repitió y aprobó tras corregir una aserción
  que buscaba el título del paso con otra capitalización.
- Seis escenarios adicionales aprobados a 1280 × 800 dp en el mismo emulador:
  cambio de cliente, conflicto con privacidad y enmascarado, foco del campo
  inválido, filtro con páginas pendientes, bloqueo etiquetado y validación con
  texto ampliado. Esta configuración verifica pantalla amplia emulada.
- Capturas nuevas revisadas de la bandeja filtrada y del campo de validación
  aislado, en teléfono y pantalla amplia. El campo se comprobó en tema oscuro
  con texto al 140 %. El diálogo también se verificó mediante sus nodos
  semánticos y acciones con texto ampliado.
- Lint, Debug, APK de pruebas y Release aprobados. Release conserva R8 y
  reducción de recursos, con firma de distribución externa al repositorio.
- `ds:check`, las ocho pruebas de `ds:test` y el build web exigidos por
  `AGENTS.md` aprobados. El build web mantiene avisos de presupuesto para el
  bundle inicial y los estilos de landing.

Los recorridos usan fixtures del APK de pruebas. La semántica accesible se
comprueba automáticamente; su lectura con TalkBack y el uso en hardware físico
siguen requiriendo validación directa. Al terminar se restauraron tamaño y
densidad del emulador.

Evidencia local de esta ejecución:

- Capturas: `/tmp/doggycredit-harden/phone/` y `/tmp/doggycredit-harden/wide/`.
- Compilación final: `/tmp/doggycredit-harden-final-build.txt`.
- Primera ejecución de teléfono: `/tmp/doggycredit-harden-phone-results.txt`.
- Confirmación del recorrido de evaluación:
  `/tmp/doggycredit-harden-confirm-results.txt`.

## Distribución del Inicio — 19 de octubre de 2026

- 42 pruebas unitarias aprobadas, sin errores ni omisiones.
- 23 ejecuciones de interfaz aprobadas en el mismo Pixel 9 Pro emulado, API 37:
  los 15 recorridos de `AuthenticatedFlowTests` a tamaño original, tres a
  320 × 800 dp, dos a 768 × 1024 dp y tres a 1280 × 800 dp. Las configuraciones
  ampliadas comprueban texto al 200 %, institución y motivos largos, score 1000
  y contadores máximos. El teléfono también conserva la cobertura al 140 %.
- Debug, Release, APK de pruebas y Lint aprobados. Lint informa cero errores y
  41 avisos previos: versiones de SDK y dependencias, vectores, icono monocromo,
  orden de parámetros Compose, compatibilidad SDK y sugerencias KTX.
- `ds:check`, las ocho pruebas de `ds:test` y el build web exigidos por
  `AGENTS.md` aprobados; permanecen los avisos de presupuesto ya documentados.

La inspección inicial detectó una barra de borrador demasiado alta y acciones
superpuestas con texto al 200 %. Se corrigieron juntas y se revisó una ronda
visual de confirmación. Las pruebas ahora comparan los límites del contenido
con la fila de acciones fijas. Se repitió la suite después de ajustar sus
selectores de navegación al árbol semántico de Material 3; todas las ejecuciones
finales aprobaron.

| Criterio | Evidencia |
| --- | --- |
| Jerarquía principal y secundaria | Las capturas de Inicio muestran contexto, trabajo pendiente y después resumen; el borrador activo precede a los casos. El plan usa texto y progreso compactos. |
| Ruta de trabajo en cada tamaño | La primera resolución queda por encima del FAB a tamaño de texto normal en las cuatro configuraciones. Con texto al 200 %, continuar, score, resolución y resumen quedan por encima de las acciones fijas. |
| Agrupación por significado | Cada título, acción y contenido de sección comparte una `Column`; los grupos internos usan separaciones de 8 o 12 dp. |
| Ritmo de espacios | Los encabezados se agrupan con su contenido a 12 dp; las métricas y el consumo mensual se separan a 16 dp, dentro del espaciado existente de `PageColumn`. |
| Densidad de uso frecuente | Los casos aparecen antes de las métricas y del plan; se conserva un máximo de dos casos en Inicio y el acceso a la bandeja completa. |
| Contenido extremo y recuperación | Fixtures con nombres y motivos largos, formatos de `Display`, contadores máximos, score 1000, texto al 200 %, error recuperable y bandeja vacía; métricas y acciones se redistribuyen sin recortar sus valores. |
| Lectura, foco y acceso | El orden de composición coincide con el visual. Los controles nativos conservan sus acciones y nombres accesibles; «Retomar» anuncia el paso actual con texto ampliado. La lectura con TalkBack y el teclado físico requieren comprobación directa. |
| Comprobación mecánica | Lint sin errores y `git diff --check` limpio. El detector web no analiza Kotlin; se usaron Lint, geometría de Compose e inspección de capturas. |

Los recorridos usan fixtures del APK de pruebas y no escriben datos financieros
en el servidor. Se restauraron tamaño y densidad del emulador, se conservó el
almacenamiento de la app y se cerró el emulador iniciado para esta revisión.
La comprobación de pantallas amplias es emulada; no sustituye hardware físico.

Evidencia local:

- Capturas y resultados finales: `/tmp/doggycredit-layout/confirmation/`.
- Compilación de la aplicación: `/tmp/doggycredit-layout-final-build.txt`.
- Compilación final de pruebas: `/tmp/doggycredit-layout-test-build.txt`.

## Pulido final — 19 de octubre de 2026

- 42 pruebas unitarias aprobadas, sin errores ni omisiones.
- 29 recorridos de interfaz comprobados en el emulador API 37: 17 a tamaño
  original, tres a 320 × 800 dp, tres a 952 × 427 dp en teléfono horizontal,
  tres a 800 × 1280 dp y tres a 1280 × 800 dp. Los 17 iniciales incluyen los
  16 de `AuthenticatedFlowTests` y la prueba específica de resultado de
  `NativeUiTests`; no se ejecutó la prueba que reemplaza el almacenamiento de
  credenciales.
- Nuevo recorrido de Perfil 360 → resultado → revisión → Analítica, en oscuro
  y con texto al 200 %. Comprueba Cuenta a un mínimo de 48 × 48 dp y contadores
  largos mediante las medidas de cada línea, con tolerancia de un píxel por
  redondeo de anchos intrínsecos.
- Resultado parcial con score 1000 y score ausente comprobados en ambos temas,
  manteniendo «Datos simulados», la explicación del resultado y la ausencia de
  razones. Score y contexto ocupan regiones separadas.
- Capturas inspeccionadas en una ronda inicial y una de confirmación. Se
  corrigieron los selectores de las pruebas para el texto «Resultado parcial»
  y el desplazamiento de listas que todavía no han compuesto el caso buscado.
  Se repitieron los tres recorridos de teléfono horizontal tras ajustar ese
  selector; todos aprobaron.
- Debug, Release, APK de instrumentación y Lint aprobados. Permanecen los mismos
  41 avisos de Lint, sin errores. `ds:check`, las ocho pruebas de `ds:test` y el
  build web exigidos por `AGENTS.md` aprobaron con los avisos de presupuesto ya
  documentados.

Los componentes usan la paleta y los roles tipográficos existentes. Las parejas
principales comprobadas alcanzan 14,27:1 para texto principal claro, 6,26:1 para
texto secundario claro, 9,00:1 para la acción clara, 7,35:1 para dorado sobre pino,
6,57:1 para la acción oscura y 10,33:1 para texto secundario oscuro. Esta medición
de colores no sustituye una auditoría completa de accesibilidad.

Las pruebas usan fixtures aisladas y tamaño de texto aplicado a la composición
nativa. La inspección comprueba lectura, redistribución y desplazamiento en
pantallas emuladas; TalkBack, teclado físico, rendimiento en hardware y otras
versiones de Android requieren validación directa. Se restauraron tamaño y
densidad del emulador, se conservaron los datos de la app y se cerró el emulador
iniciado para esta revisión.

Evidencia local:

- Capturas y resultados finales: `/tmp/doggycredit-polish/confirmation/`.
- Compilación final de la app: `/tmp/doggycredit-polish-final-build.txt`.
- Compilación final de pruebas y Lint:
  `/tmp/doggycredit-polish-final-test-build.txt`.

## Refuerzo del bloqueo — 19 de octubre de 2026

Se abordaron AUD-01 y AUD-02 de [la auditoría técnica](audit-2026-10-19.md):
salida mediante Atrás y acciones fuera de pantalla con errores largos. La
[segunda auditoría](audit-2026-10-19-revision-2.md) confirma AUD-02 y reabre
AUD-01: una prueba nueva con el NavHost real falla desde Cuenta, porque Atrás
navega por el contenido cubierto antes de ejecutar la salida protegida. Los
resultados satisfactorios que siguen corresponden al refuerzo anterior y no
incluían esa integración.
`ProtectedSession` centraliza la cobertura, el foco y el enrutamiento de Atrás;
`LockScreen` conserva su composición centrada y permite desplazamiento cuando
el contenido supera la altura disponible. El error expone una región viva
educada. La sesión y el borrador no se descartan al enviar la tarea al fondo.

- 42 pruebas unitarias aprobadas, sin errores ni omisiones.
- 32 ejecuciones de interfaz aprobadas en el Pixel 9 Pro emulado, API 37:
  cuatro del bloqueo a tamaño original; tres en cada configuración de
  320 × 800, 952 × 427, 800 × 1280 y 1280 × 800 dp; y los 16 recorridos de
  `AuthenticatedFlowTests` a tamaño original.
- `ProtectedSessionTests` verifica que la portada retire contenido y controles
  del árbol accesible, limpie el foco, bloquee toques al contenido inferior,
  preserve el borrador y dirija Atrás según el estado protegido.
- La regresión de la tarea ejecuta `moveTaskToBack(true)` desde el dispatcher
  de Atrás, espera `ON_STOP`, devuelve la tarea al frente y comprueba que sigue
  protegida al recuperar `RESUMED`. Se ejecuta sobre la actividad aislada de
  pruebas, sin activar el bloqueo de la sesión real.
- El error original de 102 caracteres y otro con emoji, árabe y CJK se
  comprueban con texto al 200 %, temas claro/oscuro y dirección LTR/RTL. Ambas
  acciones se desplazan hasta quedar visibles y ejecutan sus callbacks.
- Se inspeccionó una ronda de capturas de teléfono y tablet, en ambas
  orientaciones, sin requerir nuevas correcciones visuales. Las capturas
  horizontales muestran «Cerrar sesión» alcanzable tras desplazarse.
- Debug, Release, APK de instrumentación y Lint aprobados; permanecen los mismos
  41 avisos de Lint y cero errores. `ds:check`, las ocho pruebas de `ds:test` y
  el build web exigidos por `AGENTS.md` aprobaron con los avisos de presupuesto
  ya documentados.

La evidencia de Atrás procede del dispatcher y del ciclo de vida de una tarea
Android emulada. El gesto físico, su animación predictiva, TalkBack y el teclado
físico requieren comprobación directa. Los formatos y errores de prueba no
representan una traducción completa de la aplicación a otros idiomas.

No se reemplazaron credenciales ni se modificaron preferencias de seguridad ni
se escribieron datos financieros en el servidor. Se restauraron tamaño y
densidad originales y se cerró el emulador iniciado para esta revisión.
AUD-03, AUD-04 y AUD-05 siguen pendientes de sus ajustes específicos; la
puntuación de la auditoría original no se actualizó automáticamente.

Evidencia:

- [Captura horizontal corregida](audit-evidence/2026-10-19/lock-harden/landscape-actions-fixed.png).
- [Pantalla estrecha con Unicode y RTL](audit-evidence/2026-10-19/lock-harden/narrow-unicode-actions-fixed.png).
- [Resumen de ejecuciones](audit-evidence/2026-10-19/lock-harden/verification-summary.json).
- Resultados y capturas completos: `/tmp/doggycredit-lock-harden/initial/`.
- Compilación: `/tmp/doggycredit-lock-harden-build.txt`.

Tras los demás ajustes de auditoría, el cierre recomendado es
`$impeccable polish`.

## Resultado inicial del port

- 32 pruebas unitarias aprobadas, sin errores.
- 16 pruebas instrumentadas aprobadas en Pixel 9 Pro emulado con Android 17/API 37.
- 1 prueba instrumentada omitida: lectura completa con token real de institución,
  porque no se proporcionaron credenciales.
- Compilaciones Debug y Release correctas; Release usa R8 y reducción de recursos.
- Android Lint ejecutado sin errores. Quedan avisos informativos de versiones de
  dependencias, precisión de los vectores oficiales y sugerencias de estilo KTX.
- Navegación verificada también a 1280 × 800 dp: cambia de barra inferior a rail.
- Recorridos con tema oscuro y texto al 140 %, consentimiento accesible, Perfil
  360, historial, evaluación completa, resolución, analítica, cuenta y borrador.
- La portada de privacidad oculta el formulario y conserva su borrador y ruta.
- Keystore verificado mediante escritura, lectura, comprobación de cifrado y borrado.
- El APK Release no incluye FixtureApi, FixtureSession, documentos de fixtures ni
  acceso de demostración.
- Dos pruebas adicionales del callback de autenticación aprobadas después de
  corregir el cierre de la app al volver desde el navegador (detalle más abajo).

Comando de validación completa:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.liveTests=true
```

## Servidor real

Desde Android se verificaron el descubrimiento OIDC, la admisión del cliente
móvil con PKCE, el formulario de Keycloak y el error 401 del contrato para una
petición sin autenticación. El callback nativo también está registrado y resuelve
a la actividad de AppAuth.

No se crearon ni modificaron registros financieros en el VPS durante la validación.
Los recorridos de interfaz usan fixtures compiladas solo en el APK de pruebas.
La lectura exhaustiva de todos los endpoints con un token externo sigue siendo
una prueba opcional en `LiveServerTests.allAuthenticatedReadFlowsWithInstitutionToken`.
El inicio de sesión real se verificó posteriormente como se detalla abajo.

El backend del proyecto sigue configurando simuladores financieros. Android usa
sus respuestas y muestra `simulado` cuando corresponde; esta validación no
certifica que esas fuentes sean entidades financieras reales.

## Corrección del retorno de autenticación

El log del dispositivo identificó un cierre en
`net.openid.appauth.RedirectUriReceiverActivity`: esa actividad hereda de
`AppCompatActivity`, pero recibía el tema de plataforma de la aplicación. Se
asignó explícitamente `Theme.AppCompat.Translucent.NoTitleBar` al receptor, igual
que la actividad de gestión de AppAuth, conservando el callback exacto y PKCE.

Se añadieron dos pruebas de regresión que abren el navegador desde el botón real
de MainActivity y vuelven mediante el intent público `doggycredit://oauth/callback`:

- Un error de autorización se muestra en un diálogo y permite reintentar.
- Una respuesta con `state` diferente se rechaza sin abrir una sesión.

Ambas pruebas pasaron en el emulador. No introducen credenciales ni sesiones
ficticias en la aplicación. Ejecución focalizada:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew \
  :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.doggycredit.app.AuthCallbackTests
```

También se completó el login contra el servidor desplegado reutilizando la sesión
de institución que el usuario había abierto en Chrome. El servidor regresó al
receptor nativo, AppAuth intercambió el código con PKCE y la app abrió su inicio
autenticado con navegación a clientes, revisión y analítica. No fue necesario leer
ni almacenar la contraseña del usuario. El emulador quedó con la app instalada y
esa sesión real abierta.

## Artefactos locales

- APK instalable de desarrollo: `app/build/outputs/apk/debug/app-debug.apk`.
- Release sin firma de distribución: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Pruebas unitarias: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Pruebas de dispositivo: `app/build/reports/androidTests/connected/debug/index.html`.
- Lint: `app/build/reports/lint-results-debug.html`.
- Capturas de pruebas: `app/build/outputs/connected_android_test_additional_output/`.

Las capturas del formulario seguro pueden quedar vacías por FLAG_SECURE. Esa
protección se conserva en ambas variantes de compilación.
