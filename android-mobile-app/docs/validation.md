# Validación del port Android

Fecha: 14 de octubre de 2026.

La refactorización visual posterior y sus comprobaciones específicas están
documentadas en [interface.md](interface.md), incluida la corrección de la franja
debajo del botón flotante.

## Resultado

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
