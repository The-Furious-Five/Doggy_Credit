# Auditoría técnica de Android · revisión 3

## Veredicto de conformidad

**Pasa como aplicación nativa Android.** Se corrigieron los cuatro hallazgos
que seguían abiertos en la revisión anterior y se comprobó su integración con
el recorrido financiero. Atrás controla la portada protegida; la navegación
mantiene nombres visibles con texto grande; el acceso conserva contraste
durante la carga; el historial compone una ventana de filas.

Fecha local: 19 de octubre de 2026. Alcance: exclusivamente `android-mobile-app`.
Modo: Operate. Se aplicaron `harden`, `adapt`, `colorize`, `optimize` y `polish`,
seguidos de esta auditoría de código y evidencia nativa.

## Estado general

| Dimensión | Revisión 2 | Actual | Fundamento |
| --- | --- | --- | --- |
| Accesibilidad | 3/4 | 4/4 | Nombres visibles, selección semántica, foco protegido, errores anunciados y acciones alcanzables hasta el 200 %. El caso con animaciones del sistema desactivadas conserva la operación. |
| Rendimiento | 3/4 | 4/4 | Historial lazy con claves y tipos estables: 7 tarjetas compuestas frente a 1.000, con los mismos 1.000 registros cargados. Red asíncrona y Release de aproximadamente 2,45 MiB. Valoración estructural, sin atribuirle una tasa de fotogramas medida. |
| Apariencia y temas | 3/4 | 4/4 | Acción sobre marca compartida, roles explícitos y contraste aproximado 9:1 en carga; revisión visual de ambos temas y texto ampliado. |
| Conformidad Android | 2/4 | 4/4 | Atrás comprobado con el NavHost real, incluidos Cuenta, perfil y cajón abierto; componentes y navegación Material, insets y controles del sistema. |
| Adaptación | 3/4 | 3/4 | Seis tamaños de ventana, teléfono y tablet en ambas orientaciones, IME y texto grande. Falta evidencia específica de multitarea y posturas plegables para sostener el nivel máximo de esta dimensión. |
| **Total** | **14/20** | **19/20** | **Excelente en el alcance revisado.** |

**Hallazgos confirmados abiertos: cero P0, cero P1, cero P2 y cero P3.**
Los cinco hallazgos históricos, incluido AUD-02 cerrado previamente, quedan
cerrados con la evidencia descrita abajo. El punto restante responde a cobertura
de adaptación pendiente; no se inventa un defecto para justificarlo ni se
certifican combinaciones no probadas. La puntuación no sustituye la validación
del piloto, una revisión completa con TalkBack o pruebas de hardware.

## Cierre de los hallazgos

### AUD-01 · Cerrado · Prioridad de Atrás en la portada

En [MainActivity.kt, línea 137](../app/src/main/java/com/doggycredit/app/MainActivity.kt#L137),
el manejador protegido se registra al entrar en la portada, después de componer
el contenido y su NavHost. Al salir del estado protegido se retira. Esto evita
que el registro previo de la navegación cubierta reciba primero el evento.
La salida sigue llamando a `moveTaskToBack(true)`; no cambia ruta, bloqueo ni
borrador.

[AuditRemediationTests](../app/src/androidTest/java/com/doggycredit/app/AuditRemediationTests.kt)
comprueba la prioridad con Inicio, Cuenta y un perfil abiertos, conservación
del borrador y regreso al retroceso normal. Otro caso abre el cajón al 200 %,
activa la portada y comprueba que Atrás sigue ejecutando la salida protegida,
sin exponer los destinos cubiertos. Se ejecutaron esos casos en los seis
tamaños indicados en la matriz.

`ProtectedSessionTests` conserva las verificaciones complementarias de foco,
toques bloqueados, ocultación semántica y envío real de la actividad de prueba
al fondo, con retorno desde `ON_STOP` a `RESUMED`. No se presenta el dispatcher
de pruebas como una validación manual del gesto predictivo o de biometría.

### AUD-02 · Cierre conservado · Acciones del bloqueo alcanzables

El bloqueo mantiene desplazamiento, insets y región viva para errores largos.
Las tres regresiones de `ProtectedSessionTests` volvieron a pasar en los seis
tamaños. Se conservan claro/LTR y oscuro/RTL con texto al 200 %, emoji, árabe
y CJK. La [captura estrecha](audit-evidence/2026-10-19/revision-3/lock-narrow-rtl-200.png)
muestra la acción final alcanzable mediante desplazamiento.

### AUD-03 · Cerrado · Nombres de navegación con texto ampliado

[DoggyCreditApp.kt, línea 100](../app/src/main/java/com/doggycredit/app/ui/DoggyCreditApp.kt#L100)
usa un cajón modal Material cuando `fontScale >= 1.4f`. Sus destinos tienen
nombres completos, selección semántica y desplazamiento; se abre desde raíces
y detalles. Con tamaño de texto normal se conserva la barra o rail adaptable
a la ventana. Los iconos acompañantes son decorativos para no duplicar los
nombres que ya aporta el texto.

Las pruebas visitan los cinco destinos al 140 % y 200 %, comprueban sus nombres
y selección, y cierran el cajón mediante su acción accesible. Se revisaron las
capturas de [teléfono estrecho al 200 %](audit-evidence/2026-10-19/revision-3/navigation-narrow-200-dark.png),
[horizontal al 140 %](audit-evidence/2026-10-19/revision-3/navigation-landscape-140-light.png)
y [tablet al 200 %](audit-evidence/2026-10-19/revision-3/navigation-tablet-200-dark.png).
No se reduce el tamaño de texto elegido por la persona.

### AUD-04 · Cerrado · Contraste del acceso ocupado

El componente compartido
[BrandButton, línea 62](../app/src/main/java/com/doggycredit/app/ui/components/Components.kt#L62)
usa los roles `BrandActionColors` de los fundamentos: marfil/pino en reposo y
marfil/bosque en carga. El botón ocupado sigue deshabilitado, anuncia el cambio
de texto mediante región viva y evita repetir la operación. Su indicador es
decorativo; la etiqueta proporciona el estado accesible.

| Pareja de colores | Contraste calculado |
| --- | --- |
| Primario `Forest` sobre `Pine`, referencia nominal del defecto anterior | 1,273:1 |
| Texto e indicador `Ivory` sobre el contenedor ocupado `Forest` | 8,997:1 |
| Texto `Pine` sobre la acción normal `Ivory` | 11,450:1 |

La referencia anterior era una pareja nominal, no una medición de cada píxel
compuesto del botón deshabilitado. Los roles nuevos son opacos y explícitos.
`BrandContrastTests` verifica un mínimo de 4,5:1 para texto normal y ocupado.
Se comprobó el acceso en ambos temas y con texto al 200 %; las capturas de
[claro estrecho](audit-evidence/2026-10-19/revision-3/welcome-narrow-light-200.png)
y [oscuro horizontal](audit-evidence/2026-10-19/revision-3/welcome-landscape-dark-200.png)
muestran el estado legible y la acción alcanzable.

### AUD-05 · Cerrado · Historial virtualizado

[ClientScreen](../app/src/main/java/com/doggycredit/app/ui/screens/DetailScreens.kt#L100)
usa `PageList`, una página `LazyColumn` compartida. Perfil, fuentes,
consentimientos y evaluaciones son elementos independientes. El
[historial, línea 145](../app/src/main/java/com/doggycredit/app/ui/screens/DetailScreens.kt#L145)
usa claves por ID y un tipo de contenido común; no se agrupa toda la colección
en una fila. Se conserva el estado de desplazamiento al volver de una evaluación.

`ClientDetailModel` mantiene las filas al fallar una actualización o página,
reintenta el mismo número, deduplica evaluaciones y cancela la paginación al
actualizar. Una generación de petición impide que una respuesta tardía restaure
datos viejos. `ClientHistoryTests` cubre error/reintento, deduplicación,
actualización fallida y respuesta tardía incluso con una API de prueba que
ignora la cancelación.

**Medición reproducible del coste corregido:** `ProfileHistoryTests` carga
1.000 evaluaciones sintéticas en el perfil. Antes del cambio encontró 1.000
nodos de estado de tarjeta; después, 7 en la ventana inspeccionada: una reducción
del **99,3 %** de esos nodos compuestos. También alcanza el final del perfil y
comprueba que no quedan todas las tarjetas compuestas. Los registros de
[antes](audit-evidence/2026-10-19/revision-3/baseline-history-observation.txt)
y [después](audit-evidence/2026-10-19/revision-3/history-window-observations.txt)
conservan los recuentos. Es una medida de composición, no de FPS, memoria total
o latencia de red. Los objetos de datos de las páginas cargadas se conservan.

## Verificación y pulido

Se ejecutaron **47 pruebas unitarias: cero fallos, errores u omisiones**.
Debug, instrumentación y Release compilaron; Lint terminó con **cero errores y
41 avisos**. Son avisos de SDK/dependencias, vectores y sugerencias ya existentes,
no 41 defectos de interfaz. Los controles web exigidos por `AGENTS.md` también
pasaron: `ds:check`, las ocho pruebas de `ds:test` y compilación. Persisten los
avisos previos de presupuesto del bundle y `landing.scss`; no se modificó web.

La primera ronda nativa ejecutó 66 casos: 65 pasaron y uno falló porque el nuevo
recorrido intentaba pulsar una tarjeta fuera de la ventana compuesta en
horizontal. La confirmación ejecutó 24: 23 pasaron y el mismo recorrido llegó
al destino correcto, pero esperaba un encabezado que había quedado fuera de
pantalla al conservarse el desplazamiento. Se corrigieron ambos supuestos de
la prueba: desplazar antes de pulsar y comprobar tarjeta/destino visibles al
volver. La repetición funcional de ese caso horizontal pasó. Esos ajustes no
requirieron cambiar la interfaz.

En total se registran **91 ejecuciones posteriores a la implementación: 89
satisfactorias y dos fallos iniciales del recorrido de prueba ya resueltos**.
La prueba de rendimiento anterior al cambio falló deliberadamente con 1.000
tarjetas y se guarda aparte. No se ocultan los resultados iniciales ni se
presentan como pruebas nuevas los resultados de auditorías anteriores.

| Configuración del mismo emulador | Área aproximada | Primera ronda | Confirmación |
| --- | --- | --- | --- |
| Teléfono | 427 × 952 dp | 31/31 | 4/4 |
| Teléfono estrecho | 320 × 800 dp | 7/7 | 4/4 |
| Teléfono horizontal | 952 × 427 dp | 6/7 | 3/4; caso corregido 1/1 |
| Tablet vertical | 800 × 1280 dp | 7/7 | 4/4 |
| Tablet horizontal | 1280 × 800 dp | 7/7 | 4/4 |
| Ventana intermedia simulada | 600 × 900 dp | 7/7 | 4/4 |

El teléfono ejecutó los 16 flujos autenticados, ocho pruebas de UI base,
regresiones del bloqueo y medición del historial. Las otras configuraciones
incluyeron navegación, acceso, bloqueo y el recorrido ampliado de perfil,
resultado, revisión y analítica. La confirmación añadió cajón abierto durante
la protección y acceso al 200 %. Tablet horizontal se comprobó con las tres
escalas de animación del sistema a cero y después se restauraron.

El pulido incluyó dos inspecciones visuales en lote: nombres y selección,
lectura en ambos temas, acceso ocupado, errores largos del bloqueo, perfil y
gráficos al 200 %. No hubo cambios cosméticos posteriores a esas inspecciones.
La prueba de almacenamiento usa un directorio propio de caché y no reemplaza
el archivo real de la sesión.

## Patrones que quedan consolidados

- Las comprobaciones del bloqueo incluyen la integración con las capas que
  atienden Atrás; una prueba aislada ya no basta para cerrar ese contrato.
- Ampliar texto conserva información: se adapta la estructura de navegación
  y se redistribuyen acciones sin eliminar nombres.
- Las superficies de marca tienen roles explícitos para reposo y carga;
  `BrandButton` evita que cada pantalla reconstruya sus estados.
- `PageColumn` queda para contenido acotado; `PageList` y `remoteItems` componen
  las secciones acumulables y reutilizan los estados de recuperación.

Se conservan Material 3, identidad de marca, consentimiento explícito,
enmascarado por rol, datos simulados, scores explicados, regiones vivas,
portada de privacidad, `FLAG_SECURE` y Keystore. No se cambió ningún contrato
de negocio ni se enviaron registros financieros a la API desplegada.

## Alcance de la puntuación y siguiente validación

La evidencia procede de un Pixel 9 Pro emulado con API 37, reconfigurado en seis
tamaños. No son seis dispositivos físicos. Cambiar tamaño/densidad no equivale
a comprobar Android en pantalla dividida ni una bisagra plegable: por eso
Adaptación conserva 3/4 y el total queda en **19/20**.

Para sostener 20/20 hace falta validar las ventanas y posturas adicionales y
resolver cualquier defecto real que aparezca. También siguen fuera de esta
evidencia la lectura real con TalkBack, teclado físico, biometría y gesto
predictivo en hardware, rendimiento a 60/120 Hz y ejecución en otras versiones
de Android. No se presentan como defectos confirmados ni como pruebas superadas.

Los artefactos, recuentos, huellas SHA-256 y límites están en el
[registro de verificación](audit-evidence/2026-10-19/revision-3/verification-summary.json).
Se restauraron tamaño, densidad y ajustes de animación; se retiraron las capturas
temporales propias del dispositivo y se cerró el emulador iniciado para el trabajo.
El APK de pruebas instalado corresponde a los fuentes normales del proyecto.

No quedan correcciones de esta auditoría pendientes de implementar. Para
cambios posteriores o tras ampliar la validación, repetir `$impeccable audit`
mantiene actualizados los hallazgos y la puntuación.
