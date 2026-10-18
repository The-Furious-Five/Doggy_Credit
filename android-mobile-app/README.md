# DoggyCredit para Android

Aplicación Android nativa en Kotlin y Jetpack Compose, conectada a
**https://doggycredit.los5furiosos.cloud**. Usa la misma API, institución, cuenta y
reglas de negocio que la aplicación iOS. No hay WebView, datos precargados, acceso
de demostración ni respuestas locales que sustituyan errores del servidor.

## Abrir y ejecutar

Abrir **esta carpeta** en Android Studio, sincronizar Gradle y ejecutar `app`.
Requiere SDK 36 y JDK 17 o 21 (el JDK integrado de Android Studio es apropiado).
Compatible con Android 8.0/API 26 en adelante; objetivo Android 16/API 36.

En macOS, desde terminal:

```bash
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew assembleDebug
```

APK instalable de desarrollo: `app/build/outputs/apk/debug/app-debug.apk`.
La compilación Release usa R8 y reducción de recursos; la firma de distribución
se configura con el certificado del propietario, fuera del repositorio.
`local.properties` contiene la ubicación local del SDK y no se versiona.

## Conexión al servidor

- API: `https://doggycredit.los5furiosos.cloud/api/v1/`.
- Realm: `https://doggycredit.los5furiosos.cloud/auth/realms/doggycredit`.
- Cliente público existente: `doggycredit-mobile`.
- Callback: `doggycredit://oauth/callback`, registrado en el manifiesto Android y
  ya admitido por el cliente móvil compartido con iOS.
- Acceso: Authorization Code + PKCE S256 con AppAuth y Custom Tabs del navegador.
  Las contraseñas se escriben únicamente en Keycloak.

Se necesita una cuenta del servidor con `tenant_id`, audiencia `doggycredit-api`
y un rol asignado. La API comprueba firma, permisos e institución. Android
comprueba las claims de entorno antes de restaurar o aceptar la sesión.
Los tokens se cifran con AES-GCM mediante una clave del Android Keystore, se
guardan en `noBackupFilesDir` y se excluyen de backups y transferencias.
La renovación se serializa; un 401 reintenta una sola vez. Una caída temporal del
servidor de identidad no elimina las credenciales. Se bloquea HTTP sin TLS y no
se guarda caché de respuestas ni se registran tokens o documentos.

**Procedencia de los datos:** todas las pantallas leen y escriben en la API del
servidor. El backend de este proyecto configura simuladores de buró, banco y
billetera; cuando devuelve `simulado: true`, Android muestra «Datos simulados».
Para obtener datos de entidades financieras reales hay que configurar esos
proveedores en el backend. La app no cambia ese indicador ni inventa respuestas.

## Funcionalidades

La matriz completa está en [docs/ios-parity.md](docs/ios-parity.md):

- Inicio con revisiones pendientes, últimas evaluaciones y alertas de cuota.
- Clientes paginados, filtro por segmento y búsqueda exacta por CI o NIT.
- Perfil Financiero 360, fuentes y trazabilidad, historial paginado, consentimientos
  y revocación confirmada.
- Evaluación en cuatro pasos con consentimiento explícito por fuente, borrador de
  sesión, reintentos idempotentes, score, razones, versión y correlación.
- Revisión manual con filtros, deslizar o menú de acciones, producto, justificación
  obligatoria, registro de resolución y manejo de cambios concurrentes.
- Analítica por período, distribución de necesidades, métricas del plan y monitoreo
  de modelos cuando la API habilita el bloque avanzado.
- Cuenta, uso del plan, privacidad por rol, bloqueo biométrico o credencial del
  dispositivo, acceso a la consola y cierre de sesión local y remoto.

## Experiencia Android

Material 3 con navegación inferior en teléfono y rail adaptable en pantallas
amplias; navegación Atrás del sistema, acciones de actualización y gesto para
actualizar listados; FAB de evaluación y formularios con teclado numérico.
Los controles usan tamaños táctiles y semántica nativos. El score incluye una
descripción para TalkBack y los estados no dependen únicamente del color.
La interfaz respeta tema del sistema y tamaño de texto. El color de marca permanece
estable en vez de adoptar el fondo de pantalla del dispositivo.

Los vectores de logotipo, isotipo e iconos adaptables se derivan directamente de
los SVG de `../DoggyCredit-brand`. Los colores provienen de esa marca y de los
tonos semánticos usados por iOS. Hay un icono monocromático para launchers temáticos.
La información autenticada usa `FLAG_SECURE` y una portada al pasar a segundo
plano. El bloqueo es opcional y utiliza BiometricPrompt o la credencial del dispositivo.

El borrador vive en el ViewModel de sesión: se conserva al cerrar el formulario,
cambiar de pestaña o rotar el dispositivo. Al cerrar sesión o terminar el proceso
se descarta; los registros ya enviados permanecen en el servidor.

## Arquitectura

- `model/`: contratos serializables, formato es-BO, privacidad y evaluación.
- `data/`: interfaz de API, implementación HTTP, errores de negocio y cancelación.
- `auth/`: AppAuth, renovación de tokens y almacenamiento cifrado.
- `AppViewModel`: estado y trabajos ligados a la sesión, paginación y filtros.
- `ui/`: navegación, componentes comunes y pantallas Material 3.
- `src/test/` y `src/androidTest/`: fixtures e interceptores exclusivos de pruebas.

## Validación

```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.liveTests=true
```

Las pruebas unitarias cubren contratos, transporte HTTP, privacidad, evaluación,
idempotencia y resolución concurrente. Las instrumentadas verifican consentimiento,
accesibilidad, bienvenida, evaluación y resolución completas, privacidad,
tema oscuro, texto grande, Keystore y callback nativo. Los recorridos de interfaz
usan fixtures únicamente dentro del APK de pruebas; el APK de la app no las contiene. Con `liveTests`
se comprueba además OIDC, PKCE y autenticación requerida desde Android contra el VPS.
Estas comprobaciones no crean ni modifican registros financieros en el servidor.

La lectura de todas las pantallas con una institución concreta requiere un token
real temporal mediante el argumento de instrumentación `accessToken`; esa prueba
se omite explícitamente cuando falta. No guardar credenciales en el repositorio
ni usarlas como datos precargados.

Referencias técnicas: [Material 3 adaptable](https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation),
[AppAuth Android](https://github.com/openid/AppAuth-Android),
[BiometricPrompt](https://developer.android.com/identity/sign-in/biometric-auth).
