---
target: android-mobile-app
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:/Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/android-mobile-app/app/src/main/java/com/doggycredit/app/ui/DoggyCreditApp.kt"
target_fingerprint: "sha256:e90879c012377790d4eb26a02cb004278040adadaf5eb6799a90c7d927659bfc"
target_path: /Users/franco/Documents/UCB/Arquitectura de Software/DoggyCredit/Doggy_Credit/android-mobile-app/app/src/main/java/com/doggycredit/app/ui/DoggyCreditApp.kt
timestamp: 2026-10-19T23-00-16Z
slug: com-doggycredit-app-ui-doggycreditapp-kt-479418c7
closed: true
---
Method: design review only (detector not applicable, native platform)

# Critique — DoggyCredit Android

Alcance: android-mobile-app, usando DoggyCreditApp.kt como entrada estable. Objetivo: preparar un piloto institucional. Evaluación heurística de una interfaz operativa, basada en código y capturas nuevas con datos de pruebas.

## Design Specificity Verdict

DoggyCredit tiene una identidad reconocible: pino, dorado y marfil, titulares serif, cifras sans y un emblema propio. Los componentes mantienen coherencia entre pantallas. La mayor oportunidad está en la jerarquía operativa: la bienvenida y el consumo del plan reciben más espacio que el trabajo pendiente.

Deterministic scan: not applicable (native platform). No se utilizaron detector web, navegador ni overlays.

## Design Health Score

Puntuación orientativa de diseño, no una medida de usabilidad obtenida con participantes.

| # | Heurística | Nota /4 | Evidencia principal |
|---|---|---:|---|
| 1 | Visibilidad del estado | 3 | Progreso, cargas y confirmaciones claros; el vacío filtrado puede inducir a error. |
| 2 | Lenguaje del usuario | 3 | CI/NIT, Bs y consentimiento comprensibles; banda y PSI requieren explicación. |
| 3 | Control y libertad | 2 | Crear una evaluación puede retomar otro cliente o un resultado terminado. |
| 4 | Consistencia y estándares | 3 | Material 3, navegación adaptable y componentes coherentes. |
| 5 | Prevención de errores | 2 | Consentimiento e idempotencia presentes; falta resolver el conflicto de borradores. |
| 6 | Reconocimiento | 3 | Acciones etiquetadas y contexto visible; la corrección del formulario exige localizar el fallo. |
| 7 | Eficiencia | 2 | Un cliente existente debe completar datos que luego se ignoran. |
| 8 | Estética y síntesis | 3 | Buena identidad; el inicio retrasa el acceso al trabajo. |
| 9 | Recuperación de errores | 2 | Hay reintentos; la validación no identifica el campo inválido. |
| 10 | Ayuda contextual | 2 | Buen texto de consentimiento; faltan definiciones para interpretar resultados. |
| **Total** | | **25/40** | **Aceptable; requiere mejoras antes del piloto.** |

## What's Working

- La combinación de marca, tipografía y cifras mantiene una identidad consistente y una lectura clara.
- La navegación cambia de barra inferior a rail en pantalla amplia. La captura oscura con texto al 140 % mantiene legibles los elementos observados.
- El consentimiento separa fuentes y parte sin autorizaciones seleccionadas. Los resultados muestran razones y trazabilidad; la resolución confirma qué quedó registrado.

## Priority Issues

### 1. [P1] Crear y retomar una evaluación se confunden

Con un borrador del cliente A, abrir el perfil de B y pulsar «Nueva evaluación» conserva A sin advertir del conflicto: startEvaluation ignora el documento solicitado cuando hay un borrador en curso. Además, tras cerrar un resultado con «Listo», el FAB puede volver a abrir ese resultado, porque no reinicia el borrador terminado.

Esto rompe la expectativa del analista y aumenta el riesgo de trabajar con el cliente equivocado.

Fix: distinguir «Retomar» de «Nueva evaluación». Ante un conflicto, mostrar el cliente del borrador respetando el enmascarado y ofrecer retomarlo, descartarlo para iniciar el cliente seleccionado o cancelar. Un resultado finalizado debe permitir iniciar una evaluación nueva.

Source: app/src/main/java/com/doggycredit/app/AppViewModel.kt:161; ui/screens/DetailScreens.kt:96; ui/screens/EvaluationScreen.kt:46.
Suggested command: $impeccable harden.

### 2. [P1] La bandeja puede comunicar un vacío falso

Los filtros de Revisión se aplican únicamente a las páginas cargadas. Si las coincidencias están en una página posterior, aparece «Sin casos pendientes» aunque aún queda información por cargar. El botón «Cargar más casos» ofrece un camino de salida, pero el mensaje contradice el alcance real del filtro.

Fix: filtrar la bandeja completa o comunicar explícitamente que no hay coincidencias entre los casos cargados y ofrecer continuar la búsqueda. Reservar el vacío definitivo para cuando se conoce todo el conjunto.

Source: app/src/main/java/com/doggycredit/app/ui/screens/ListsScreens.kt:103; AppViewModel.kt:129.
Suggested command: $impeccable harden.

### 3. [P2] «Continuar» se bloquea sin señalar qué corregir

El primer paso utiliza un mensaje general para múltiples reglas. Por ejemplo, un plazo de 361 meses mantiene el botón deshabilitado, sin indicar junto al plazo el límite aceptado. Los campos no incorporan isError ni ayuda específica de validación.

Fix: mostrar restricciones junto al campo, validar después de la interacción y dirigir el foco al primer error cuando corresponda. Explicar el problema concreto en lugar de repetir todos los requisitos.

Source: app/src/main/java/com/doggycredit/app/model/EvaluationDraft.kt:35; ui/screens/EvaluationScreen.kt:82.
Suggested command: $impeccable harden.

### 4. [P2] Los clientes existentes requieren una captura innecesaria

Incluso al comenzar desde un perfil, se exige completar actividad, ingreso y otros datos para avanzar. La búsqueda del cliente se hace después. Si existe, esos datos no actualizan el registro: recién en Consentimiento se avisa que se usarán los guardados.

Esto consume tiempo y deja dudas sobre qué información alimentó la evaluación.

Fix: identificar primero al cliente y presentar sus datos existentes antes de solicitar la información de la nueva evaluación. Separar el alta del solicitante de la solicitud de crédito, sin prometer una actualización de datos que el flujo no realiza.

Source: app/src/main/java/com/doggycredit/app/model/EvaluationDraft.kt:51; ui/screens/EvaluationScreen.kt:111.
Suggested command: $impeccable shape.

### 5. [P2] El inicio da prioridad al plan sobre los casos

En las capturas iniciales de teléfono y pantalla amplia, el saludo, la tarjeta de consumo y las métricas desplazan «Resolver caso» fuera del área visible. La función más relevante para resolver trabajo pendiente requiere desplazamiento, mientras la información del plan ya dispone de una sección en Cuenta.

Fix: compactar saludo y consumo; colocar borrador y trabajo pendiente antes del detalle del plan. Mantener las alertas de cuota visibles cuando requieren atención y conservar la identidad actual.

Source: app/src/main/java/com/doggycredit/app/ui/screens/HomeScreen.kt:25.
Visual evidence: /tmp/doggycredit-critique/phone/home.png; /tmp/doggycredit-critique/wide/home.png.
Suggested command: $impeccable layout.

## Cognitive Load

Carga moderada: fallan tres de ocho criterios heurísticos — foco de tarea en Inicio, memoria de trabajo al localizar errores y revelación progresiva para clientes existentes. Agrupación, jerarquía interna de tarjetas y secuencia de cuatro pasos ayudan a comprender el recorrido.

Hay cinco destinos principales visibles, dentro del patrón nativo observado; no constituyen por sí solos una sobrecarga. El primer paso concentra segmento, seis campos y cuatro destinos de crédito antes de descubrir que el cliente ya existe.

## Emotional Journey

La identidad y el acceso institucional transmiten cuidado. La fricción aparece durante la captura obligatoria y cuando un botón deshabilitado no explica el problema concreto. El consentimiento aporta tranquilidad; la confirmación «Caso resuelto» cierra bien la tarea. El score necesita una explicación contextual de la escala y la banda para ayudar a interpretar el resultado.

## Persona Red Flags

- Jordan, primer uso: no puede localizar fácilmente qué campo impide continuar cuando incumple un rango.
- Casey, usuario interrumpido: al volver desde otro perfil puede retomar el cliente anterior sin una decisión explícita.
- Sam, lector de pantalla: el Switch de bloqueo no tiene una etiqueta semántica propia ni una fila toggleable que lo vincule al texto; los avisos dinámicos tampoco declaran liveRegion. Son riesgos detectados por código, pendientes de comprobar directamente con TalkBack.

## Minor Observations

- La escala «de 1000» está en la descripción accesible del score, pero no se muestra junto a la cifra visible.
- «Evaluaciones recientes» abre el perfil del cliente; llegar a esa evaluación requiere pasar por el historial.
- Con texto al 140 %, PYME queda fuera del tramo inicial de la fila horizontal de filtros. Sigue siendo desplazable, pero conviene hacer evidente esa continuidad.

## Evidence and Limits

Se compilaron Debug y el APK de pruebas. Pasaron ocho recorridos de AuthenticatedFlowTests en Pixel_9_Pro y tres recorridos con un viewport amplio de 1280 × 800 dp en el mismo emulador. Se inspeccionaron las capturas nuevas de Inicio, Inicio desplazado, Clientes oscuro con texto al 140 %, Analítica, resultado desde el historial y resolución.

El archivo evaluation-result.png captura el fondo de Inicio, no el diálogo: se descartó como evidencia visual del resultado modal. La revisión de ese diálogo y de los escenarios de las incidencias 1–4 se apoya en el código; esos escenarios no se reprodujeron mediante nuevas pruebas. Los datos usados son fixtures del APK de instrumentación, sin operaciones financieras contra el servidor. La pantalla amplia es una configuración emulada, no una prueba en tablet física.

## Questions to Consider

- ¿Qué cambio reduciría más el tiempo entre abrir la app y resolver el siguiente caso?
- ¿Cómo debería responder «Nueva evaluación» cuando ya existe trabajo de otro cliente?
