# Registro de deuda técnica — DoggyCredit MVP

Cada deuda lleva: descripción, riesgo si no se paga, **disparador de pago**
(el evento que obliga a pagarla) y dueño. Se revisa en cada retro. En el
código se marca con `// DEBT-NNN` para poder buscarla con grep.

Fuente: `DoggyCredit — Visión, Requerimientos y Arquitectura v1`, §17.3.

| ID | Deuda aceptada | Disparador de pago |
|---|---|---|
| DEBT-001 | Una VM y una réplica, sin alta disponibilidad; respaldos manuales | Primer cliente piloto |
| DEBT-002 | Sin broker: eventos en proceso con outbox de Spring Modulith | Primer consumidor externo (webhooks, analítica, ML en línea) |
| DEBT-003 | Rate limit en memoria (Bucket4j local) y jobs sin bloqueo distribuido | Segunda réplica de la API |
| DEBT-004 | Fuentes simuladas (WireMock); consentimiento sin validez legal verificada | Convenio con la primera fuente real |
| DEBT-005 | Modelo de scoring con datos sintéticos, sin monitoreo de deriva (PSI) | Antes de cualquier decisión con datos reales |
| DEBT-006 | Clave maestra en secreto de Docker/`.env`, sin KMS ni rotación | Antes de cargar datos personales reales |
| DEBT-008 | Analítica sobre vistas de la base transaccional | p95 de analítica > 1 s o más de 1 M de filas |
| DEBT-009 | Cadena de auditoría sin ancla WORM externa | Primer piloto o auditor externo |
| DEBT-010 | Sin autoservicio de registro ni facturación real | Salida comercial |
| DEBT-011 | Java 21 en lugar de 25 | Después del MVP |
| DEBT-012 | El logout no revoca los access tokens vigentes (5 min) | Requisito de revocación inmediata |
| DEBT-013 | Los payloads crudos de las fuentes no se persisten (solo hash) | Primer reclamo o disputa que necesite reprocesar |
| DEBT-014 | 4 roles en el MVP: `TENANT_ADMIN` concentra política, usuarios y auditoría | Primera entidad supervisada exigente |
| ~~DEBT-015~~ | ~~`ddl-auto: update` temporal, porque Spring Modulith no distribuye el SQL de su tabla interna `event_publication` para Flyway~~ | **Pagada** (V2026_09_29_1402, SPIKE-1): migración escrita a mano, `ddl-auto: validate` de nuevo. El disparador que ya vencía con `Consent` se cumplió, y dejó de ser viable de todas formas: con el rol restringido de SPIKE-1, la app ya no tiene privilegio para alterar el esquema |
| ~~DEBT-016~~ | ~~El artefacto `scoring-v1.onnx` y `golden_set.json` se copian a mano de `ml/model/` a `backend/src/main/resources/` y `backend/src/test/resources/` después de cada entrenamiento; el CI no los regenera ni los valida entre sí~~ | **Pagada** (D-07, LOS-94): el job `ml` de `ci-cd.yml` reentrena desde cero, exige que `ml/model/` quede igual (`git diff`) y que `backend/` tenga los mismos bytes (`ci/check_ml_artifacts.py`, por SHA-256). Para que el hash sea estable entre macOS y Linux, `train.py` ajusta en float64 con Newton y redondea los parámetros (`PARAM_DECIMALS`). La copia a `backend/` sigue siendo manual, pero ya no puede quedar desactualizada sin romper el CI |
| ~~DEBT-018~~ | ~~La app inicia sesión en PostgreSQL como `doggy_app` (superusuario, por ser el `POSTGRES_USER` de la imagen oficial) y baja a `doggycredit_app` con `SET ROLE` en cada conexión (SPIKE-1). `SET ROLE` es reversible dentro de la sesión: cualquier SQL que la app llegara a ejecutar con `RESET ROLE` (p. ej. vía inyección SQL) vuelve a ser superusuario y se salta RLS. Hoy no hay SQL construido con datos del usuario (JPA con parámetros), pero la defensa no es de capa de base de datos pura~~ | **Pagada** (V2026_10_04_2100): Se independizaron los usuarios. Flyway conserva admin, la app conecta directo con rol LOGIN propio restringido, sin requerir SET ROLE |
| DEBT-017 | Cada tabla nueva necesita, en la misma migración que la crea, RLS (`ENABLE` + `FORCE` + política `tenant_isolation`) y su `GRANT` a `doggycredit_app` — PostgreSQL no tiene privilegios por defecto para esquemas futuros. Mitigada: `RowLevelSecurityCoverageTests` (LOS-57) rompe el build si una tabla con `tenant_id` no tiene RLS completo; el `GRANT` faltante se detecta en cuanto un test toca la tabla (permission denied) | Si aparece una tabla sin `tenant_id` que igual necesite aislamiento, o un módulo sin tests de integración |

**Deuda que no se acepta** (lecciones de MedicalSys): pruebas omitidas
contadas como aprobadas, endpoints sin prueba de aislamiento de tenant,
contratos escritos a mano en vez de generados, "modos locales" que
esconden una integración real, un segundo formato de error, IDs de
demo duplicados entre Keycloak y la base de datos.
