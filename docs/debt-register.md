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
| DEBT-016 | El artefacto `scoring-v1.onnx` y `golden_set.json` se copian a mano de `ml/model/` a `backend/src/main/resources/` y `backend/src/test/resources/` después de cada entrenamiento; el CI no los regenera ni los valida entre sí | Cuando el pipeline de `ml/` entre al CI (Sprint 2/3): un paso que reentrene, copie y verifique que el `.onnx` en `backend/` coincide con el de `ml/model/` (por hash) |
| DEBT-018 | La app inicia sesión en PostgreSQL como `doggy_app` (superusuario, por ser el `POSTGRES_USER` de la imagen oficial) y baja a `doggycredit_app` con `SET ROLE` en cada conexión (SPIKE-1). `SET ROLE` es reversible dentro de la sesión: cualquier SQL que la app llegara a ejecutar con `RESET ROLE` (p. ej. vía inyección SQL) vuelve a ser superusuario y se salta RLS. Hoy no hay SQL construido con datos del usuario (JPA con parámetros), pero la defensa no es de capa de base de datos pura | Antes de cargar datos personales reales: separar credenciales — Flyway con el rol administrador, la app con un rol `LOGIN` propio sin superusuario (contraseña en `.env`/secretos) — y quitar el `SET ROLE` |
| DEBT-017 | El rol restringido `doggycredit_app` (SPIKE-1) solo tiene privilegio sobre `consent.consents`, `tenancy.tenants` y `event_publication`. Cualquier módulo nuevo (`assessment`, `scoring`, `audit`, ...) necesita su propio `GRANT` en la migración que crea su esquema — PostgreSQL no tiene "privilegios por defecto para esquemas futuros" | Cada vez que un módulo nuevo agregue su primera tabla |

**Deuda que no se acepta** (lecciones de MedicalSys): pruebas omitidas
contadas como aprobadas, endpoints sin prueba de aislamiento de tenant,
contratos escritos a mano en vez de generados, "modos locales" que
esconden una integración real, un segundo formato de error, IDs de
demo duplicados entre Keycloak y la base de datos.
