# Presentación final de DoggyCredit (A-17 / LOS-144)

Guion de diapositivas para el lunes 19 de octubre de 2026, con la identidad
`DoggyCredit-brand` (pino `#10403C`, miel `#D59132`, arena `#F7DDA0`, marfil
`#FFFEFD`; ver `docs/design-system/README.md`). Cada diapositiva dice qué
mostrar y **de dónde sale la evidencia** en el repositorio, para que nada de lo
que se diga sea distinto de lo que se puede demostrar.

Marcas: **[Construido]** está en `main` y se puede demostrar · **[Diseñado]**
existe en el informe de arquitectura o en un contrato, sin implementación ·
**[Pendiente]** depende de una historia abierta al momento de escribir esto.

> Falta incorporar el material de dos historias de otras personas, sin las que
> la presentación no está completa: las vistas C4 y el ADR de **B-15
> (LOS-142)** y el guion de demo, las cuentas y el video de respaldo de **C-17
> (LOS-143)**. Los huecos están marcados con ⚠.

---

## Sección 1 · Problema y objetivo (2 min)

**1. Portada.** DoggyCredit · Los 5 Furiosos · logo sobre superficie de marca.

**2. El problema.** Las IMF, cooperativas y fintech pequeñas deciden crédito con
datos dispersos (buró, banco, billetera) y sin un score explicable propio.

**3. Qué es DoggyCredit.** SaaS multi-tenant de evaluación crediticia:
consentimiento → Perfil Financiero 360 → score explicable → decisión y
recomendación de producto. Seis atributos, un ícono cada uno:
altamente integrable · basada en datos · escalable · segura · preparada para
ML/IA · freemium (Gratis y Pro).

---

## Sección 2 · Arquitectura (5 min)

**4. Vistas C4.** ⚠ Insertar contexto y contenedores de B-15 (LOS-142).
Mientras tanto: informe de arquitectura §9.4–9.5 (`docs/c4/README.md`) y los
diagramas que Spring Modulith genera del código en
`backend/target/spring-modulith-docs/` (`ApplicationModulesTest`).
Mensaje: monolito modular (Spring Modulith) + SPA Angular + apps móviles,
Keycloak, PostgreSQL con RLS, Caddy y observabilidad OTLP/Grafana.

**5. Decisiones.** 18 ADR del informe (§13.1) + nuevas en `docs/adr/`:
ADR-019 (consumo entre tenants con función de lectura bajo RLS). ⚠ Sumar el ADR
de B-15.

**6. Atributos de calidad demostrados** (una diapositiva por atributo o una
tabla; cada fila con su evidencia):

| Atributo | Cómo se demuestra | Evidencia |
| --- | --- | --- |
| Aislamiento con RLS | RLS forzado en toda tabla con `tenant_id`; la app entra con un rol sin superusuario ni `BYPASSRLS`; meta-test que recorre el contrato | `RowLevelSecurityCoverageTests`, `RowLevelSecurityConcurrencyTest` (2 tenants, hilos concurrentes, sin cruces), `OpenApiTenantIsolationTests`, `ProductionDataSourceWiringTest` |
| Contrato estándar de conector | Un conector por fuente detrás del mismo puerto; el segundo banco es otro adaptador, no otro flujo | `integration/internal/BankJson.java` y `BankDosJson.java`; credenciales propias por tenant (E-15) |
| ONNX dentro de la JVM | El modelo se entrena en `ml/`, se exporta a ONNX y corre en ONNX Runtime dentro del backend; el CI reentrena y exige los mismos bytes | `OnnxScoringAdapter.java`, `ml/README.md`, job 5 de CI, `ScoreExplanationGoldenSetTest` |
| Cifrado por campo | AES-256-GCM con DEK por tenant envuelta por clave maestra | `AesGcmFieldEncryption.java`; documentos cifrados en `customer.applicants` (`ApplicantControllerTests`) |
| Auditoría encadenada | Cada registro encadena el hash del anterior; `verify_chain` detecta la cola borrada o reescrita | `V2026_10_05_1000__audit_hash_chain.sql`, LOS-114 |
| Carga con k6 | Dos tenants concurrentes, p95 < 2 s por tenant, 0 cruces (la lectura ajena debe dar 404) | `e2e/k6/credit-assessments.js`, `docs/runbooks/carga-k6-e07.md`, workflow `k6-load.yml` |

---

## Sección 3 · Trabajo del equipo (4 min)

**7. Scrum en cuatro sprints.** Sprint 1 «esqueleto seguro que camina» · Sprint 2
planes, cuotas, seguridad y configuración · Sprint 3 cambio de plan, despliegue,
MFA y bandeja de revisión · Sprint 4 contratos, KPIs, E2E y cierre.
Contract-first (OpenAPI → clientes Java y Angular generados, `oasdiff` en CI),
Design System obligatorio con checker, CI de 7 jobs con despliegue a la VM.

**8. Lo construido [Construido]** (agrupar por área; cada integrante presenta lo
suyo):
- Tenancy y seguridad: RLS, Keycloak multi-tenant con PKCE, MFA para
  administradores, rate limit por tenant, enmascarado de documentos y montos en
  el servidor, suspensión de tenants.
- Planes: Gratis/Pro con `Entitlements`, cuota mensual idempotente, gates por
  plan, subida inmediata y bajada con 30 días de gracia, administración por API.
- Evaluación: consentimiento, Perfil 360, score ONNX explicable, políticas,
  bandeja de revisión manual con override auditado, elegibilidad y ranking de
  productos.
- Plataforma: webhooks firmados, claves de API, analítica, auditoría encadenada,
  apps móviles, despliegue con smoke test.

**9. Lo diseñado, no construido [Diseñado].** Del informe y los contratos:
`GET /operador/metricas` (contrato A-15, implementación D-14) ⚠ actualizar si
LOS-134 se fusiona; facturación real y autoservicio de registro (DEBT-010);
broker de eventos (DEBT-002); KMS y rotación de claves (DEBT-006).

**10. Decisiones de alcance.** Fuentes simuladas con WireMock; pago simulado en
el cambio de plan; la solicitud «Mejorar a Pro» se retiró de la consola para no
prometer una contratación inexistente; una VM sin alta disponibilidad.

**11. Deuda técnica registrada.** De `docs/debt-register.md`: abiertas
DEBT-001 a 006, 008 a 014 y 017; pagadas DEBT-015, 016 y 018 (rol propio de la
app, A-05). Mostrar la columna «disparador de pago».

---

## Sección 4 · Demostración (6 min)

**12. Demo en vivo.** ⚠ Seguir el guion de C-17 (LOS-143) y sus cuentas.
Propuesta de recorrido, solo con lo que está en `main` y cubierto por E2E:
1. Analista de un tenant Gratis: evaluación con consentimiento → resultado con
   score y razones (`credit-flow.spec.ts`).
2. Gates: el tenant Gratis ve la invitación a Pro en Analítica
   (`plan-gates.spec.ts`).
3. Operador: sube el tenant a Pro y, sin volver a iniciar sesión, aparecen las
   funciones Pro (`plan-upgrade.spec.ts`).
4. Bandeja de revisión: override justificado que queda en la auditoría
   (`manual-review.spec.ts`).

**13. Cierre.** Qué aprendimos y siguiente paso (piloto: dispara DEBT-001,
006 y 009).

---

## Lista de verificación antes del lunes

- [ ] Vistas C4 y ADR de B-15 incorporados (diapositivas 4 y 5).
- [ ] Guion, cuentas y video de C-17 incorporados (diapositiva 12).
- [ ] Cada integrante revisó su parte de la diapositiva 8.
- [ ] Capturas reales en lugar de los marcadores (por ejemplo, el operador
      cambiando el plan).
- [ ] Ensayo completo con cronómetro dentro del tiempo asignado.
