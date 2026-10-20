# Interfaz Android de DoggyCredit

## Identidad y componentes

La interfaz usa el logo y el isotipo vectoriales de `DoggyCredit-brand`. La paleta
parte del verde `#10403C`, el marfil `#FFFEFD` y el dorado `#EECA7D` presentes en
esos archivos. El fondo cálido, los títulos con serif y las cifras con tipografía
sans distinguen la jerarquía sin introducir otra familia de marca descargable.

Los componentes comunes están en `ui/components/Components.kt`: cabeceras,
paneles de marca, métricas, indicadores de score, estados, filtros y contenedores
de página. Los colores, formas y estilos de texto se definen en `ui/theme`.

### Contratos compartidos para acceso e historial

| Componente | Contrato nativo |
| --- | --- |
| `BrandButton(label, onClick, modifier, busy)` | Botón Material sobre pino. `busy` impide pulsaciones, anuncia el cambio de texto mediante región viva y muestra progreso decorativo sin duplicar el nombre accesible. Usa roles `BrandActionColors`; marfil sobre bosque en carga, con contraste aproximado 9:1 en ambos temas. Acceso y desbloqueo comparten el componente. |
| `PageList(modifier, state, content)` | Página `LazyColumn`, con el ancho, insets, espacio inferior e IME de las páginas comunes. Cada sección o fila acumulable se declara como un elemento independiente. Conserva el estado nativo de desplazamiento. |
| `LazyListScope.remoteItems(key, state, retry, content)` | Equivalente lazy de `RemoteContent`: error recuperable, progreso, carga inicial y datos conservados, sin agrupar un historial entero en una fila. Las claves de estado llevan el prefijo de la sección. |

`ClientScreen` utiliza `PageList` para perfil, fuentes, historial y consentimientos.
Las evaluaciones usan claves por ID y un tipo de contenido común; las páginas
se deduplican al agregarse. El control de página siguiente cambia de clave al
crecer el historial para no desplazar la ventana hasta el nuevo final. Volver
desde una evaluación conserva la posición del perfil. Actualizar cancela la
paginación anterior y descarta respuestas tardías; un fallo mantiene las filas
cargadas y permite reintentar la misma página.

- Inicio: actividad mensual, cartera, pendientes y evaluaciones recientes.
- Clientes: fichas por segmento y perfil financiero con métricas destacadas.
- Revisión: estado, razones, score y acceso a la resolución de cada caso.
- Analítica: indicadores del período y distribución de necesidades del servidor.
- Evaluación: cuatro etapas con progreso, selección y consentimiento explícitos.
- Cuenta: identidad, institución, uso del plan y controles de seguridad.

Las cifras y el gráfico se calculan exclusivamente con las respuestas de la API.
Cuando un dato aún no está disponible se muestra `—`; no se sustituyen valores
faltantes por cifras de ejemplo. Se conservan el enmascaramiento por rol, las
advertencias de fuentes simuladas y la protección de contenido sensible.

## Borradores, validación y bandeja

«Nueva evaluación» inicia un formulario vacío cuando el resultado anterior ya
terminó. Si hay trabajo en curso, un diálogo permite retomarlo, cancelar o
descartarlo para evaluar al cliente seleccionado. Los documentos del diálogo
respetan el enmascarado de la sesión y el contenido se oculta con la portada de
privacidad. No se permite descartar durante una operación pendiente.

`ui/components/Components.kt` expone `ValidatedTextField`: un campo Material con
`validationError`, `hint` y `submitted`. El error se muestra al perder el foco
después de editar o al intentar continuar; conserva el valor, marca el estado
inválido y expone el mensaje a accesibilidad. En la evaluación se muestran los
rangos de cada campo y el primer error recibe foco. `Notice` anuncia sus cambios
como una región de estado y el interruptor de bloqueo usa toda su fila como
control etiquetado.

Los filtros de revisión se aplican a los casos cargados y muestran cuántos se
revisaron respecto del total. Un resultado sin coincidencias distingue entre
páginas pendientes, un filtro sin resultados y una bandeja vacía. Cargar más
conserva los casos anteriores ante errores; actualizar cancela las peticiones
anteriores para que una respuesta tardía no restaure información vieja. Después
de resolver un caso se actualiza la bandeja desde su primera página.

## Bloqueo y salida segura

`MainActivity` utiliza `ProtectedSession` para conservar el contenido detrás de
la portada, retirarlo del árbol accesible y limpiar su foco mientras la sesión
está protegida. Al entrar en la portada se registra su manejador de Atrás
después del contenido, con prioridad sobre el NavHost. Llama a
`moveTaskToBack(true)` sin cambiar el bloqueo, la ruta cubierta ni el borrador.
Fuera del estado protegido, el manejador se retira y Atrás continúa con la
navegación habitual. `AuditRemediationTests` comprueba la integración con
Inicio, Cuenta y un perfil del NavHost real, además de la prueba aislada de
fondo/retorno de la tarea.

`LockScreen` conserva su composición centrada cuando cabe y permite desplazarse
cuando el texto o la altura lo requieren. «Desbloquear» y «Cerrar sesión» siguen
alcanzables con errores largos y texto al 200 %. Los cambios del error usan una
región viva educada. Durante la cobertura transitoria, sin acciones de
desbloqueo o cierre, el contenido inferior conserva su protección frente a
toques y accesibilidad.

Las regresiones están en `ProtectedSessionTests`: foco, borrador y enrutamiento
de Atrás; envío real de la actividad de prueba al fondo y regreso protegido;
errores largos en ambos temas, Unicode y dirección RTL. Se complementan con las
regresiones integradas del NavHost en `AuditRemediationTests`. No reemplazan
credenciales ni activan el bloqueo de la sesión real.

## Contenido bajo el botón flotante

El Inicio sigue un orden de trabajo: contexto breve de la institución, borrador
activo, avisos que requieren atención, casos por resolver, resumen de actividad
y evaluaciones recientes. El título y las acciones de cada sección se agrupan
con su contenido. El consumo del plan usa la variante `compact` de
`UsageOverview` en Inicio; Cuenta conserva la presentación completa.

Métricas, acciones del borrador y encabezado de la bandeja se redistribuyen
cuando no caben en una fila. Las tarjetas de revisión e historial reservan
espacio para el score según el tamaño de texto y permiten apilarlo con el
resumen. El orden de lectura y foco coincide con el orden visual.

Se eliminó el `padding(bottom = 72.dp)` externo del `NavHost`, que recortaba el
contenido y dejaba una franja fija de otro color detrás de «Evaluar». El espacio
para el botón se aplica ahora dentro del contenido desplazable mediante
`LocalPageBottomPadding`, también en las listas de clientes y revisión. Así el
contenido ocupa todo el área y el último elemento puede desplazarse por encima
del botón. La barra de borrador conserva su propio espacio cuando corresponde.

La navegación sigue siendo Material 3: barra inferior en teléfono y rail en
pantallas amplias, botón flotante, retroceso del sistema y actualización por gesto.
Con texto al 140 % o superior, las acciones «Evaluar» y «Retomar» ocupan una
fila propia dentro del Scaffold, sin superponerse al contenido. La navegación
usa un cajón modal Material con nombres completos, selección semántica y
desplazamiento; se abre con «Abrir navegación» desde raíces y detalles. Sus
iconos son decorativos porque el texto ya da nombre a cada destino. La portada
de privacidad cierra el cajón y bloquea su interacción. El botón
«Retomar» también anuncia el paso actual del borrador. El contenido mantiene
el tamaño de texto elegido por el usuario y puede desplazarse hasta el final.

## Acabado de componentes nativos

Los componentes comunes viven en `ui/components/Components.kt` y usan la
tipografía, colores y formas de `ui/theme/` sobre Material 3:

- `Score(score, band, modifier, size, onDark)` conserva su tamaño tipográfico
  nominal y amplía su contenedor según el tamaño de texto. No requiere que cada
  pantalla calcule ese espacio. Mantiene un único nombre accesible, incluidos
  score y banda ausentes.
- `ScorePanel(score, band, content)` compone el panel de marca con score y
  contexto. Apila el contexto cuando la fila no conserva un ancho legible.
  Resultado y resolución comparten este patrón; la decisión o el documento
  preceden a la etiqueta de contexto, sin duplicar encabezados.
- `MetricGrid(values, icons)` admite pares de etiqueta y valor e iconos
  opcionales. Usa como máximo dos columnas, separación de 12 dp y un ancho
  mínimo de 160 dp ajustado al tamaño de texto y al contenedor. Lo usan Inicio,
  Perfil Financiero 360 y Analítica, conservando formatos y enmascarado.

El gráfico de necesidades ajusta su tamaño con el texto y redistribuye la
leyenda. Su descripción accesible reúne categorías y cantidades sin repetir el
texto del centro. Las barras superiores muestran un solo título; los títulos
largos conservan su texto semántico completo aunque se abrevien visualmente.
Cuenta dispone de un área de 48 × 48 dp y una separación de 8 dp respecto de
Actualizar. Se conservan los controles nativos, las decisiones, los avisos de
resultado parcial y el indicador de datos simulados.

## Verificación

El pulido posterior aprobó 29 ejecuciones en cinco configuraciones emuladas,
incluidas ambas orientaciones de teléfono y tablet, texto al 200 %, score 1000,
resultado parcial y score ausente en claro y oscuro. Se comprobaron las métricas
con contadores largos, el acceso a Perfil 360, resultado, resolución y Analítica,
y el área mínima de Cuenta. El detalle está en
[validation.md](validation.md#pulido-final--19-de-octubre-de-2026).

La revisión del 19 de octubre aprobó los 15 recorridos de
`AuthenticatedFlowTests` en teléfono y ocho ejecuciones adicionales repartidas
entre 320 × 800, 768 × 1024 y 1280 × 800 dp. Incluyen borrador, errores, estados
vacíos, texto al 200 %, nombres largos y comprobaciones geométricas para evitar
que las acciones fijas tapen el contenido. Las capturas se inspeccionaron en una
ronda inicial y una de confirmación. Las 42 pruebas unitarias, Debug, Release y
Lint pasaron; Lint conserva 41 avisos previos y ningún error.

Estas pruebas usan fixtures aisladas en el APK de instrumentación. Se restauraron
el tamaño y la densidad originales del Pixel 9 Pro y se conservó el almacenamiento
de la aplicación. Los resultados y sus límites están en
[validation.md](validation.md#distribución-del-inicio--19-de-octubre-de-2026).

Para probar la interfaz sin desinstalar la aplicación ni eliminar una sesión
existente, se instalaron ambos APK con `adb install -r` y se ejecutó:

```bash
adb shell am instrument -w \
  -e class com.doggycredit.app.AuthenticatedFlowTests \
  com.doggycredit.app.test/androidx.test.runner.AndroidJUnitRunner
```

Las capturas de esta revisión se guardan en
`/tmp/doggycredit-layout/confirmation/`; contienen datos de pruebas de interfaz
y no representan una institución real.
