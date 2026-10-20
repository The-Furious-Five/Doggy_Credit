# Hardening de DoggyCredit iOS — 19 de octubre de 2026

Ámbito: `ios-mobile-app/`. Se refuerzan los flujos y los estados identificados
en la crítica de Impeccable, con controles nativos y el sistema de diseño existente.

## Cambios

| Problema | Comportamiento resultante |
| --- | --- |
| Otro cliente con una evaluación en curso | Se identifica el borrador y se permite continuar, descartar y empezar otra solicitud, o cancelar. El diálogo captura la solicitud confirmada aunque SwiftUI cierre la presentación antes de ejecutar la acción. |
| Reevaluación con datos que se descartaban | La ficha conocida aparece en solo lectura; se capturan monto, plazo y destino de la nueva solicitud. Si se descubre el cliente durante el alta, se presenta su ficha y se exige continuar explícitamente. No se simula una actualización que la API móvil no ofrece. |
| Filtro sin coincidencias en la página actual | El vacío informa que quedan casos por cargar. Buscar más casos funciona aun sin filas visibles y la última fila filtrada puede disparar la siguiente página. |
| Fallo al actualizar o paginar | Se conservan los registros ante fallos transitorios, se muestra la última actualización y se ofrece reintento. Un 403 retira los datos conservados, tanto al actualizar como al cargar otra página. |
| Respuestas tardías | Las respuestas de una consulta o segmento anterior se descartan. Cambiar o borrar el documento invalida el resultado de búsqueda anterior. |
| Cambio de titular o entrada inválida | Cambiar el documento invalida consentimientos y resultados previos. Se rechazan montos no positivos, antigüedades negativas y plazos fuera de 3–120 meses. Los campos se bloquean durante el envío del paso. |
| Texto grande | Los avisos se apilan mediante AdaptiveStack. En la bienvenida, el acceso forma parte del contenido desplazable con tamaños de accesibilidad y precede a la lista de capacidades; el espacio del icono también escala. |

## Componentes y pruebas

`DesignSystem/Components/LoadState.swift` incorpora `RefreshStatusView` y
`PaginationFooter`. `NoticeCard` reutiliza `AdaptiveStack` para permitir que el
texto accesible ocupe el ancho disponible. Las pantallas mantienen las decisiones
de negocio fuera de esos componentes.

`ios-mobile-appTests/HardeningTests.swift` añade regresiones para cambio de
borrador, recuperación de la solicitud confirmada, reevaluación, consentimiento
por titular, validación, filtros con páginas sin coincidencias, errores y
reintentos, permisos revocados y respuestas fuera de orden.

`HardeningScreensTests` presenta las vistas reales en una ventana nativa del
simulador con servicios sintéticos exclusivos de pruebas. Captura los estados de
Clientes, Revisión, ficha existente, conflicto de borrador y bienvenida, con temas
claro/oscuro y texto normal/máximo. Las capturas adicionales muestran desplazamiento
cuando el contenido lo requiere. Los dobles no se incluyen en la app distribuida.

## Validación final

- Suite iOS en iPhone 18 Pro, iOS 27: resumen de Xcode con 70 casos aprobados,
  3 pruebas opcionales de servidor omitidas y 0 fallos. La suite incluye casos
  parametrizados; el número de ejecuciones individuales es mayor.
- Capturas en iPad Air de 11 pulgadas (M4), iOS 27: prueba aprobada, sin fallos.
- UI de acceso: ambas pruebas aprobadas, incluida la protección contra el
  antiguo argumento de demostración.
- `npm run ds:check`, `npm run ds:test` y `npm run build` desde `../web/`: correctos,
  conforme a `../AGENTS.md`. Las ocho pruebas del checker pasaron. El build web
  informa advertencias de tamaño en el bundle inicial y `landing.scss`.
- `git diff --check -- ios-mobile-app`: correcto.

Resultados y dispositivos: [validation.json](hardening-evidence/2026-10-19/validation.json).
Capturas y metadatos: [iPhone](hardening-evidence/2026-10-19/iphone/manifest.json)
e [iPad](hardening-evidence/2026-10-19/ipad/manifest.json).

Ejemplos: [ficha registrada](hardening-evidence/2026-10-19/iphone/existing-applicant-light.png),
[búsqueda de casos filtrados](hardening-evidence/2026-10-19/iphone/review-filter-light.png)
y [acceso con texto accesible](hardening-evidence/2026-10-19/ipad/welcome-dark-accessibility-middle.png).

## Reproducir

Desde `ios-mobile-app/`, usando un simulador disponible:

```sh
xcodebuild test -project ios-mobile-app.xcodeproj -scheme ios-mobile-app \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
  -parallel-testing-enabled NO -only-testing:ios-mobile-appTests
```

Las pruebas de pantalla adjuntan sus imágenes al resultado de XCTest. El manifiesto
conserva el identificador del test y el dispositivo que produjo cada captura.

La evidencia corresponde a simuladores y fixtures; no constituye una prueba de
una sesión institucional autenticada ni de gestos o accesibilidad en hardware real.
El siguiente paso de acabado es `$impeccable polish`.
