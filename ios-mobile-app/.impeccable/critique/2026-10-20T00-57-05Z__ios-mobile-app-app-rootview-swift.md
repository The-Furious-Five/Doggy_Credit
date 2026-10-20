---
target: "ios-mobile-app: crítica de la app nativa iOS"
total_score: 24
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/App/RootView.swift"
target_fingerprint: "sha256:f4e773ff7eb8c078a0c39bbb4a9ee14605a43e5a86cb2d5232fc924f3ef836a0"
target_path: /Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/App/RootView.swift
timestamp: 2026-10-20T00-57-05Z
slug: ios-mobile-app-app-rootview-swift
closed: true
---
Method: design review only (detector not applicable, native platform)

**DoggyCredit iOS: 24/40 — aceptable, con problemas importantes en los flujos.**

**Identidad e impresión general.** La bienvenida tiene una identidad reconocible y una propuesta concreta para trabajar en campo. La marca, el idioma y la secuencia consentimiento → fuentes → resultado corresponden al producto. La mayor oportunidad es mejorar la confianza al retomar clientes, interpretar listados y repetir evaluaciones.

**Alcance y evidencia.** Revisión exclusiva de `ios-mobile-app`, desde `ios-mobile-app/App/RootView.swift`, en modo Operate. Compilación Debug para simulador correcta. Inspección visual de la bienvenida en iPhone 18 Pro e iPad Air de 11 pulgadas, iOS 27, con texto normal/tema claro y máximo tamaño de accesibilidad/tema oscuro. Sin sesión autenticada: los hallazgos de evaluación, clientes y revisión proceden del código, no de una ejecución completa de esos flujos. La puntuación es heurística y provisional.

**Evaluación heurística**

| Heurística | Nota /4 | Motivo principal |
|---|---:|---|
| Visibilidad del estado | 2 | Algunas actualizaciones y páginas fallan sin aviso visible. |
| Lenguaje y mundo real | 3 | Español claro y términos institucionales coherentes. |
| Control y libertad | 2 | Un borrador puede imponerse al cliente que se intenta evaluar. |
| Consistencia y estándares | 3 | Navegación y formularios nativos; buena base compartida. |
| Prevención de errores | 2 | Falta resolver explícitamente el conflicto entre clientes. |
| Reconocimiento sobre memoria | 3 | Pasos, razones y fuentes dan contexto; el borrador introduce ambigüedad. |
| Flexibilidad y eficiencia | 2 | Reevaluar obliga a repetir datos que luego se descartan. |
| Estética y simplicidad | 3 | Identidad clara; desequilibrio con texto grande y espacio excesivo en iPad. |
| Recuperación de errores | 2 | Conservar listas es útil, pero algunos fallos quedan ocultos. |
| Ayuda y documentación | 2 | Hay explicaciones locales; falta una salida de ayuda accesible desde la app. |
| **Total** | **24/40** | **Aceptable; requiere correcciones relevantes.** |

**Lo que funciona**

- La bienvenida comunica qué permite hacer la app y presenta una única acción principal clara.
- El consentimiento explica finalidad, vencimiento y revocación; las fuentes opcionales empiezan desactivadas.
- Por código, la evaluación ofrece cuatro pasos, borrador durante la sesión, idempotencia y razones del resultado. La revisión exige justificación y controla conflictos del servidor.

**Cinco prioridades**

1. **[P1] «Reevaluar a este cliente» puede abrir el borrador de otro.** Si existe una evaluación de A y se elige reevaluar B, `prefill` ignora B y se abre la hoja existente. La intención del botón y el cliente activo dejan de coincidir. **Corrección:** identificar al titular y ofrecer continuar A, descartar y empezar B, o cancelar. **Comando:** `$impeccable harden`. Evidencia: [EvaluationFlowModel.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Evaluation/EvaluationFlowModel.swift:106>) y [SessionStore.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/App/SessionStore.swift:71>).

2. **[P1] Los filtros pueden ocultar casos pendientes de otras páginas.** La bandeja filtra únicamente los casos cargados; la siguiente página depende de mostrar la última fila sin filtrar. Si esa fila queda excluida, la carga no avanza. Puede aparecer «Sin casos con este filtro» aunque existan coincidencias posteriores. **Corrección:** permitir avanzar independientemente de las filas visibles y distinguir «sin coincidencias cargadas» de una búsqueda completa. **Comando:** `$impeccable harden`. Evidencia: [ReviewQueueModel.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Review/ReviewQueueModel.swift:32>) y [ReviewQueueView.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Review/ReviewQueueView.swift:80>).

3. **[P1] Reevaluar solicita datos nuevos que no guarda.** El formulario vuelve a exigir actividad e ingreso. Si el documento ya existe, la lógica reutiliza el solicitante sin actualizar esos campos; tampoco los incluye en la petición de evaluación. El analista puede creer que renovó información que la app descarta. **Corrección:** precargar la ficha existente, explicar qué información se utiliza y permitir cambios solo mediante un flujo que realmente los guarde. **Comando:** `$impeccable shape reevaluación de clientes existentes`. Evidencia: [EvaluationSteps.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Evaluation/EvaluationSteps.swift:25>) y [EvaluationFlowModel.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Evaluation/EvaluationFlowModel.swift:155>).

4. **[P2] Algunos fallos se presentan como listados normales.** Clientes y Revisión muestran el error inicial si la lista está vacía, pero mantienen el contenido sin aviso al fallar una actualización. Los errores de paginación también se descartan. En campo es difícil distinguir información vigente, incompleta o desactualizada. **Corrección:** conservar contenido con aviso de actualización fallida, fecha de actualización y reintento explícito al pie. **Comando:** `$impeccable harden`. Evidencia: [ClientsView.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Clients/ClientsView.swift:51>) y [ClientsModel.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Clients/ClientsModel.swift:49>).

5. **[P2] La bienvenida pierde jerarquía con texto accesible.** En iPhone, el botón y el dominio del servidor ocupan más de media pantalla y reducen mucho el área de lectura. En iPad, el icono de «Evaluación en campo» invade el encabezado porque conserva una columna fija mientras crece el símbolo. **Corrección:** adaptar la disposición al tamaño de texto, incorporar la acción al desplazamiento cuando haga falta, relegar el dominio a detalles del entorno y escalar el espacio del icono. **Comando:** `$impeccable adapt`. Evidencia: [captura de iPhone](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/.impeccable/critique/evidence/2026-10-19/iphone-dark-accessibility.png>) y [captura de iPad](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/.impeccable/critique/evidence/2026-10-19/ipad-dark-accessibility.png>).

**Carga cognitiva y recorrido emocional.** Carga moderada: tres de ocho criterios presentan problemas —memoria del cliente activo, un selector con 12 plazos y falta de distinción progresiva entre alta y reevaluación—. Funcionan el foco inicial, la fragmentación en pasos, la agrupación, la jerarquía con texto normal y la secuencia general. El recorrido transmite claridad al entrar y tranquilidad al explicar el consentimiento; pierde confianza cuando se retoma otro cliente, se descartan datos introducidos o una lista parece completa sin serlo.

**Personas y señales concretas**

- **Casey, analista interrumpido en campo:** al pasar de A a B puede retomar el borrador equivocado; una caída de red deja listas anteriores sin aviso suficiente.
- **Jordan, primera reevaluación:** vuelve a introducir actividad e ingreso y no recibe indicación de que esos cambios no se guardaron.
- **Sam, persona con baja visión:** el acceso fijo y el dominio compiten con el contenido; el encabezado y el icono se superponen en iPad con texto máximo.

**Observaciones menores**

- En iPad con texto normal, el acceso queda muy separado del contenido; conviene una composición más compacta. [Captura de iPad](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/.impeccable/critique/evidence/2026-10-19/ipad-light.png>).
- Buscar mantiene el resultado previo mientras se modifica un documento no vacío y no descarta respuestas antiguas que lleguen tarde. Asociar cada resultado a la consulta vigente. [ClientSearchView.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Search/ClientSearchView.swift:25>).
- La correlación se ofrece seleccionable, pero solo contiene los primeros 13 caracteres y una elipsis. Añadir copia del valor completo para soporte. [AssessmentDetailView.swift](</Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/ios-mobile-app/ios-mobile-app/Features/Clients/AssessmentDetailView.swift:87>).

**Escaneo determinista:** no aplicable a la plataforma nativa. No se ejecutó el detector web.

**Preguntas para decidir la siguiente intervención**

1. ¿Qué priorizamos: identidad y reevaluación de clientes; bandeja y recuperación de errores; o accesibilidad visual?
2. ¿Qué alcance prefieres: los tres P1; los cinco hallazgos; o un único hallazgo?
