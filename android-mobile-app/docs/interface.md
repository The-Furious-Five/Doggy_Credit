# Interfaz Android de DoggyCredit

## Identidad y componentes

La interfaz usa el logo y el isotipo vectoriales de `DoggyCredit-brand`. La paleta
parte del verde `#10403C`, el marfil `#FFFEFD` y el dorado `#EECA7D` presentes en
esos archivos. El fondo cálido, los títulos con serif y las cifras con tipografía
sans distinguen la jerarquía sin introducir otra familia de marca descargable.

Los componentes comunes están en `ui/components/Components.kt`: cabeceras,
paneles de marca, métricas, indicadores de score, estados, filtros y contenedores
de página. Los colores, formas y estilos de texto se definen en `ui/theme`.

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

## Contenido bajo el botón flotante

Se eliminó el `padding(bottom = 72.dp)` externo del `NavHost`, que recortaba el
contenido y dejaba una franja fija de otro color detrás de «Evaluar». El espacio
para el botón se aplica ahora dentro del contenido desplazable mediante
`LocalPageBottomPadding`, también en las listas de clientes y revisión. Así el
contenido ocupa todo el área y el último elemento puede desplazarse por encima
del botón. La barra de borrador conserva su propio espacio cuando corresponde.

La navegación sigue siendo Material 3: barra inferior en teléfono y rail en
pantallas amplias, botón flotante, retroceso del sistema y actualización por gesto.

## Verificación

Se validaron los ocho recorridos de `AuthenticatedFlowTests`, incluido el nuevo
caso `homeContentScrollsBehindFabAndLastCardIsReachable`, y se revisaron capturas
con tema claro, oscuro y texto al 140 %. Estas pruebas usan fixtures aisladas en
el APK de instrumentación; la aplicación instalada continúa usando la API real.
También pasaron los recorridos de navegación con borrador y de desplazamiento
bajo el botón en una pantalla de 1280 × 800 dp, con rail de navegación. Al terminar
se restauró el tamaño original del Pixel 9 Pro y se abrió MainActivity con la
sesión real conservada. Las 32 pruebas unitarias, Debug, Release y Lint pasaron.

Para probar la interfaz sin desinstalar la aplicación ni eliminar una sesión
existente, se instalaron ambos APK con `adb install -r` y se ejecutó:

```bash
adb shell am instrument -w \
  -e class com.doggycredit.app.AuthenticatedFlowTests \
  com.doggycredit.app.test/androidx.test.runner.AndroidJUnitRunner
```

Las capturas de esta revisión se guardan en
`/tmp/doggycredit-ui-refresh/test-screenshots/`; contienen datos de pruebas de
interfaz y no representan una institución real.
