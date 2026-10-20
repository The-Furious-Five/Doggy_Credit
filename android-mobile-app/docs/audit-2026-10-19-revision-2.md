# Auditoría técnica de Android · revisión 2

**Seguimiento:** la [revisión 3](audit-2026-10-19-revision-3.md) aplica y verifica
las cuatro correcciones pendientes: **19/20 y ningún hallazgo confirmado abierto**.
El contenido siguiente conserva la instantánea previa de 14/20 y la evidencia
que permitió reabrir AUD-01.

## Veredicto de conformidad

**Pasa como aplicación nativa Android.** Compose, Material 3, iconos Material,
navegación adaptable y controles del sistema conservan una interacción propia
de la plataforma. **Mantiene un P1 de navegación:** con Cuenta abierta, Atrás
es recibido por la navegación cubierta, no por la salida protegida. La tarea
no abandona la pantalla y su navegación interna cambia sin verse. AUD-01 se
reabre; el cierre anterior comprobaba el bloqueo aislado, sin el NavHost real.

Fecha local: 19 de octubre de 2026. Alcance exclusivo: `android-mobile-app`.
Modo: Operate. Auditoría de código y evidencia nativa posterior al refuerzo del
bloqueo. Esta revisión modifica documentación, no la interfaz.

## Estado general

| Dimensión | Original | Actual | Evidencia y límite principal |
| --- | --- | --- | --- |
| Accesibilidad | 2/4 | 3/4 | El bloqueo conserva acciones alcanzables, limpia el foco y oculta el contenido subyacente del árbol semántico. La navegación aún pierde nombres visibles desde el 140 % de texto. |
| Rendimiento | 3/4 | 3/4 | Red asíncrona y listas principales lazy; el historial acumulable del perfil sigue componiéndose completo. |
| Apariencia y temas | 3/4 | 3/4 | Temas propios en claro y oscuro; el indicador de acceso ocupado pierde contraste en claro. |
| Conformidad Android | 2/4 | 2/4 | La salida mediante Atrás funciona en el bloqueo aislado, pero el NavHost real la precede con Cuenta abierta. Se reabre AUD-01. |
| Adaptación | 3/4 | 3/4 | Bloqueo comprobado en teléfono estrecho, horizontal y tablet con texto al 200 %. Persiste la pérdida de nombres de navegación con texto ampliado; multiwindow y plegables no están comprobados. |
| **Total** | **13/20** | **14/20** | **Bueno: resolver el P1 de Atrás antes de distribuir.** |

**Cuatro hallazgos abiertos: cero P0, uno P1, tres P2 y cero P3.**
La subida de un punto corresponde a la recuperación de las acciones del bloqueo
y su gestión del foco. No se aumenta la puntuación solo
porque las pruebas terminen correctamente. La escala es la misma de la
[auditoría original](audit-2026-10-19.md); no es la escala de la crítica de UX
ni una certificación de accesibilidad o de preparación del piloto institucional.

## Estado de las correcciones del bloqueo

### AUD-01 · Cierre previo insuficiente · Atrás en el bloqueo aislado

En [MainActivity.kt, línea 75](../app/src/main/java/com/doggycredit/app/MainActivity.kt#L75),
la salida de `ProtectedSession` llama a `moveTaskToBack(true)`. El manejador
de la [línea 128](../app/src/main/java/com/doggycredit/app/MainActivity.kt#L128)
ya no tiene un cuerpo vacío. Al protegerse, se limpia el foco y se retira la
semántica del contenido subyacente sin eliminar su estado.

Las pruebas de [ProtectedSessionTests.kt, línea 41](../app/src/androidTest/java/com/doggycredit/app/ProtectedSessionTests.kt#L41)
comprueban conservación del borrador, retirada de foco, ocultación semántica,
bloqueo de toques al contenido cubierto y retorno al manejador normal al dejar
de proteger. La prueba de la [línea 84](../app/src/androidTest/java/com/doggycredit/app/ProtectedSessionTests.kt#L84)
comprueba además que Android acepta enviar la tarea de prueba al fondo,
alcanza `ON_STOP` y vuelve a `RESUMED` conservando el contenido oculto.

Esa verificación usa una actividad aislada sin la navegación real de la app.
La prueba nueva de esta auditoría demuestra que no basta para cerrar AUD-01:
el NavHost puede recibir el evento antes que `ProtectedSession`. Las mejoras de
foco y privacidad siguen siendo válidas. No se probó biometría ni gesto físico.

### AUD-02 · Cerrado · Acciones del bloqueo con error y texto grande

[LockScreen, línea 138](../app/src/main/java/com/doggycredit/app/MainActivity.kt#L138)
permite desplazamiento y conserva una altura mínima igual al área disponible.
El mensaje tiene región viva `Polite` en la línea 146. Las acciones no dependen
de que todo el contenido quepa simultáneamente en pantalla.

La prueba de la [línea 117](../app/src/androidTest/java/com/doggycredit/app/ProtectedSessionTests.kt#L117)
alcanza y pulsa ambas acciones al 200 % con un error largo en claro y con
Unicode y dirección RTL en oscuro. Se ejecutó en las cinco configuraciones de
tamaño indicadas abajo. Las capturas de
[horizontal](audit-evidence/2026-10-19/lock-harden/landscape-actions-fixed.png)
y [teléfono estrecho](audit-evidence/2026-10-19/lock-harden/narrow-unicode-actions-fixed.png)
muestran «Cerrar sesión» alcanzable. El recorte del logo al desplazar el
contenido horizontal es parte del desplazamiento, no otra pérdida de acceso.

## Hallazgos abiertos por prioridad

### AUD-01 · P1 · Atrás navega por el contenido oculto antes de abandonar el bloqueo

- **Ubicación:** [ProtectedSession, línea 128](../app/src/main/java/com/doggycredit/app/MainActivity.kt#L128),
  que registra su `BackHandler` antes de componer `content()` en la línea 132;
  [NavHost real, línea 153](../app/src/main/java/com/doggycredit/app/ui/DoggyCreditApp.kt#L153).
- **Categoría:** conformidad Android.
- **Reproducción:** montar `DoggyCreditApp` dentro del `ProtectedSession` real
  usando sesión/API sintéticas, abrir Cuenta, activar la portada y enviar un
  evento al dispatcher de Atrás de la actividad. La composición utiliza el
  mismo orden y componentes que `MainActivity`.
- **Evidencia nueva:** la prueba
  [AuditNavigationProbeTests](audit-evidence/2026-10-19/revision-2/AuditNavigationProbeTests.kt)
  espera una llamada a la salida y conservar Cuenta. Observa `leaves=0` y
  `account_retained=false`: el NavHost vuelve a Inicio mientras la portada
  permanece visible. El [resultado](audit-evidence/2026-10-19/revision-2/results-navigation-back.txt)
  contiene un fallo de aserción, y el
  [registro de observación](audit-evidence/2026-10-19/revision-2/probe-observations.txt)
  conserva ambos valores. No es un fallo de compilación o infraestructura.
- **Impacto:** la primera pulsación no ejecuta la salida segura anunciada y
  modifica la ruta financiera sin mostrarla. Se necesita otra pulsación cuando
  la pila subyacente ya no puede retroceder. No se observó exposición de datos;
  tampoco se afirma pérdida de un borrador en esta reproducción.
- **Criterio:** Atrás debe tener un efecto coherente con la pantalla visible;
  la portada protegida debe controlar su salida sin navegar por contenido
  cubierto. La referencia Android exige no interceptar Atrás sin una respuesta
  útil para el usuario.
- **Recomendación:** asegurar la prioridad del manejador protegido frente a
  la navegación interna mientras la portada está activa, conservando la pila
  y los borradores. Añadir una prueba con `DoggyCreditApp` y su NavHost real,
  tanto desde una raíz como desde Cuenta y un detalle, antes de repetir la
  comprobación de fondo/retorno de la tarea.
- **Comando sugerido:** `$impeccable harden`.

### AUD-03 · P2 · La navegación pierde sus nombres visibles al ampliar texto

- **Ubicación:** [DoggyCreditApp.kt, línea 81](../app/src/main/java/com/doggycredit/app/ui/DoggyCreditApp.kt#L81)
  y asignación de `label` en la línea 99.
- **Categoría:** accesibilidad y adaptación.
- **Evidencia:** `fontScale >= 1.4f` produce `label = null` en todos los destinos.
  La [captura al 140 %](audit-evidence/2026-10-19/navigation-large-text.png)
  muestra los cinco iconos sin nombres. El archivo coincide por SHA-256 con la
  implementación que produjo esa captura. Los iconos conservan
  `contentDescription = destination.label`; no son controles sin nombre
  accesible.
- **Impacto:** quien amplía texto y navega visualmente debe reconocer los
  iconos para distinguir Revisión, Clientes o Analítica. El ajuste elimina
  información necesaria para descubrir destinos.
- **Criterio:** la adaptación al tamaño de texto debe conservar la
  identificación de las acciones y destinos, según la referencia Android de
  Impeccable. Es un problema de comprensión visual, no un fallo confirmado de
  lectura con TalkBack.
- **Recomendación:** adaptar la navegación conservando nombres visibles y
  legibles con componentes Material apropiados al ancho. Verificar al menos
  320 dp y tablet, selección, descripciones y texto al 140 % y 200 %.
- **Comando sugerido:** `$impeccable adapt`.

### AUD-04 · P2 · El progreso del acceso pierde contraste en tema claro

- **Ubicación:** [WelcomeScreen.kt, línea 35](../app/src/main/java/com/doggycredit/app/ui/screens/WelcomeScreen.kt#L35),
  indicador en la línea 37; roles en `ui/theme/Theme.kt`.
- **Categoría:** apariencia y temas.
- **Evidencia:** el acceso ocupado deshabilita el botón; su indicador usa el
  primario del tema sobre la superficie fija de marca. `Forest` y `Pine` tienen
  un contraste nominal aproximado de 1,3:1. Las capturas de
  [claro](audit-evidence/2026-10-19/welcome-busy-light.png) y
  [oscuro](audit-evidence/2026-10-19/welcome-busy-dark.png) confirman que en claro
  el progreso apenas se distingue. Pantalla y tema no cambiaron desde esas
  capturas. La ratio nominal no mide todos los píxeles del botón compuesto.
- **Impacto:** se debilita la señal de actividad mientras se conecta a la
  institución. No se contabiliza el texto deshabilitado como una infracción
  WCAG de contraste: el hallazgo es la visibilidad del progreso activo.
- **Criterio:** los roles de primer plano deben corresponder a su superficie
  y estado en ambas apariencias, también sobre paneles de marca.
- **Recomendación:** definir colores adecuados para carga y botón
  deshabilitado sobre `Pine`, conservando la prevención de pulsaciones
  duplicadas. Comprobar el estado ocupado en ambos temas.
- **Comando sugerido:** `$impeccable colorize WelcomeScreen`.

### AUD-05 · P2 · El historial acumulable no está virtualizado

- **Ubicación:** [DetailScreens.kt, línea 62](../app/src/main/java/com/doggycredit/app/ui/screens/DetailScreens.kt#L62),
  `ClientScreen` en la línea 84 y `history.items.forEach` en la línea 130;
  [PageColumn, línea 38](../app/src/main/java/com/doggycredit/app/ui/components/Components.kt#L38).
- **Categoría:** rendimiento.
- **Evidencia:** cada página concatena elementos al historial y compone todas
  las tarjetas dentro de un `Column` con `verticalScroll`. No hay límite local
  de páginas ni composición lazy para ese historial. Clientes y Revisión sí
  usan `LazyColumn`; Inicio solicita una cantidad acotada de evaluaciones.
- **Impacto:** composición y memoria crecen con las páginas cargadas, incluso
  para tarjetas fuera de pantalla. Es un coste estructural comprobado; no se
  afirma una caída de fotogramas ni una latencia medida.
- **Criterio:** las listas acumulables deben componer el contenido necesario
  para la ventana visible, con identidad estable de sus elementos.
- **Recomendación:** usar una lista lazy con secciones y claves por evaluación,
  conservando paginación, recuperación y posición. Medir después con varias
  páginas y un historial extenso en hardware representativo.
- **Comando sugerido:** `$impeccable optimize ClientScreen`.

## Patrones y aspectos que conservar

La adaptación de las tarjetas conserva información al redistribuirla, mientras
la navegación aún la elimina. Conviene extender el criterio de conservación de
contenido a esa superficie. Los estados de carga sobre marca necesitan roles
propios de primer plano; heredar el primario del tema no garantiza contraste.
`PageColumn` sigue siendo apropiado para contenido acotado, pero el historial
acumulable necesita el patrón lazy que ya existe en los listados principales.

La prueba del bloqueo aislado no cubría la precedencia entre su manejador y la
navegación real. Las pruebas de componentes deben complementarse con una
comprobación de integración cuando varias capas atienden el mismo evento del
sistema. Los 32 recorridos anteriores pasaron, pero no incluían ese caso.

Se mantienen buenas bases: controles Material 3, navegación por barra/rail,
texto en `sp`, tipografía común, temas completos, scores escalables, métricas
que se redistribuyen y descripción semántica de gráficos. Cuenta conserva un
área de 48 × 48 dp. Los campos presentan errores específicos y llevan el foco
al primero inválido; los avisos usan regiones vivas.

La red se ejecuta de forma asíncrona cancelable y el almacenamiento de sesión
usa `Dispatchers.IO`; no se encontró red síncrona bloqueando el hilo principal.
El Release existente pesa aproximadamente 2,45 MiB. Se conservan consentimiento
explícito, enmascarado, indicadores de datos simulados, portada de privacidad,
`FLAG_SECURE` y almacenamiento mediante Keystore.

Los literales de color en los fundamentos y las superficies intencionales de
marca no se clasifican automáticamente como defectos. Dynamic Color no es un
requisito del contexto de marca confirmado.

## Evidencia y límites

Se revisaron la implementación actual del bloqueo, navegación, acceso,
historial, componentes, temas y rutas de red/sesión. Al comparar las 21 huellas
Kotlin de la primera auditoría, solo cambió `MainActivity.kt`, precisamente la
superficie reforzada. Los otros tres hallazgos conservan implementación y
evidencia visual válidas. Se revisaron juntas cinco capturas nativas, sin
necesitar capturas adicionales para constatar esos hallazgos.

Se reutilizaron **42 pruebas unitarias sin fallos, errores ni omisiones** y
**32 ejecuciones nativas satisfactorias del último refuerzo**. No se ejecutaron
de nuevo ni se suman a un supuesto total de pruebas nuevas de esta auditoría.
Los APK Debug y de instrumentación son posteriores a los fuentes respectivos.

Además, **se ejecutó un diagnóstico nuevo con el NavHost real: una prueba,
un fallo de aserción**, que reabre AUD-01. Utiliza código temporal de
instrumentación incorporado mediante un init script externo; se conserva una
copia como evidencia documental, fuera de `app/src`. No usa la API desplegada.
Se inició un emulador API 37 para este diagnóstico y se restauró después el APK
normal de pruebas. No se cambió tamaño/densidad ni la configuración de la sesión.

| Configuración emulada | Área aproximada | Ejecuciones reutilizadas |
| --- | --- | --- |
| Teléfono | 427 × 952 dp | 4 |
| Teléfono estrecho | 320 × 800 dp | 3 |
| Teléfono horizontal | 952 × 427 dp | 3 |
| Tablet vertical | 800 × 1280 dp | 3 |
| Tablet horizontal | 1280 × 800 dp | 3 |
| Flujos autenticados en teléfono | 427 × 952 dp | 16 |
| **Total** | Emulador Pixel 9 Pro, API 37 | **32** |

Los cinco tamaños son configuraciones del mismo emulador, no cinco dispositivos
físicos. Los escenarios de bloqueo incluyen claro/LTR y oscuro/RTL al 200 %;
no implican que cada prueba recorra todas las combinaciones de tema y texto.

Se invocó nuevamente `:app:lintDebug`: **cero errores y 41 avisos**, con análisis
vigente reutilizado por Gradle (`UP-TO-DATE`). Los avisos incluyen dependencias,
SDK, vectores y sugerencias de implementación; no equivalen a 41 defectos de
interfaz. El [registro de esta revisión](audit-evidence/2026-10-19/revision-2/verification-summary.json)
conserva procedencia, recuentos, huellas y alcance de las verificaciones.

No se usaron navegador ni detector web para analizar Compose. No se activó una
sesión financiera real, no se enviaron datos de negocio y no se modificó código
de producción. No se repitieron pruebas web porque la revisión solo documenta
Android.

Quedan fuera de la evidencia: uso real de TalkBack, teclado físico, gesto
predictivo y biometría en hardware, rendimiento a 60/120 Hz, multiwindow,
posturas de plegables y versiones distintas de API 37. No se consideran fallos
confirmados ni pruebas superadas. Compatibilidad declarada con API 26 no
equivale a haber ejecutado esta revisión en esa versión.

## Acciones recomendadas

1. **P1 · `$impeccable harden`**: garantizar la prioridad de Atrás en la portada
   con el NavHost real y conservar la ruta cubierta.
2. **P2 · `$impeccable adapt`**: mantener nombres visibles de navegación con
   texto ampliado en teléfono y tablet.
3. **P2 · `$impeccable colorize WelcomeScreen`**: recuperar la visibilidad del
   progreso al conectar, en ambos temas.
4. **P2 · `$impeccable optimize ClientScreen`**: virtualizar el historial y
   medir el resultado con varias páginas.
5. **Cierre · `$impeccable polish`**: comprobar la consistencia final de los
   cambios.

Puedes pedir estas acciones una por una, juntas o en el orden que prefieras.
Repite `$impeccable audit` después de las correcciones para actualizar los
hallazgos y la puntuación.
