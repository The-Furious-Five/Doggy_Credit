# `contracts/events/`

Esquemas JSON de los eventos de dominio publicados por el outbox de Spring
Modulith (`@Externalized`). Formato: `<NombreEvento>.v<N>.schema.json`, en
pasado (`CreditAssessmentCompleted`, no `CompleteCreditAssessment`).

Vacío en el Sprint 1 a propósito: los primeros eventos reales
(`ConsentGranted`, `CreditAssessmentCompleted`) los agrega quien implemente
el módulo correspondiente (`consent`, `assessment`), no se inventan aquí de
antemano.
