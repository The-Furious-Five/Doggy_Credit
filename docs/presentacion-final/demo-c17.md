# Demo final de DoggyCredit (C-17 / LOS-143)

Guion de la demostración sobre <https://doggycredit.los5furiosos.cloud>, con la
versión que despliega C-15 (LOS-137). Completa la diapositiva 12 de
[guion.md](guion.md). Todo lo que se muestra está en `main` y lo cubre un E2E
(`e2e/tests/`), así que el recorrido no depende de nada «en construcción».

## Antes de empezar (10 min antes)

1. `curl -s https://doggycredit.los5furiosos.cloud/release.json` devuelve el SHA
   de `git rev-parse origin/main`, y la última ejecución de *CI/CD* sobre `main`
   tiene el job **7 · Desplegar** en verde (smoke test incluido).
2. **No se integra nada a `main` durante la demo**: cada push vuelve a desplegar
   y reinicia la API.
3. Dos ventanas del navegador, en modo claro y al 100 % de zoom: una para el
   tenant **Gratis** (Progreso Andino) y otra para el **Pro** (Fintech Vecina).
4. Tener abierto el video de respaldo (ver más abajo) por si fallan la VM o la
   conexión.

## Cuentas de demo

Usuarios sembrados en `seed/tenants.yaml` (los mismos que usan los E2E). **Las
contraseñas de producción no están en el repositorio**: solo en el `.env` de la
VPS. Las pide al equipo quien tenga acceso a la VM; no se escriben en las
diapositivas ni en el chat.

| Usuario | Tenant | Plan | Rol | Para qué |
| --- | --- | --- | --- | --- |
| `analista.progreso` | Progreso Andino | Gratis | ANALYST | Evaluación, elegibilidad, bandeja |
| `admin.progreso` | Progreso Andino | Gratis | TENANT_ADMIN | Analítica con invitación a Pro, auditoría |
| `admin.vecina` | Fintech Vecina | Pro | TENANT_ADMIN | Ranking de productos, KPIs de cartera |
| `operador.demo` | (operador) | — | PLATFORM_ADMIN | Consola del operador, cambio de plan |

Los **TENANT_ADMIN** sembrados piden **TOTP** (A-14): quien presente con
`admin.*` configura el autenticador en su teléfono antes de la demo, no durante.

Titulares sintéticos (WireMock, sin datos reales de personas):
`CI-DEMO-0001001` (buen pagador: cumple los criterios de al menos un producto) y
`CI-DEMO-0001002` (cae en revisión manual).

## Propuesta de Álvaro: que el ingeniero pruebe en vivo

**Pendiente de decidir con el equipo.** Si se acepta, se recomienda darle
`analista.progreso`: no pide TOTP, no puede cambiar políticas ni planes y ve los
montos enmascarados (A-10). Lo que haga queda en la auditoría del tenant Gratis
y consume su cuota mensual; antes de la demo hay que comprobar que quedan
evaluaciones disponibles en *Resumen*.

## Recorrido (6 min)

Quién presenta cada parte es una **propuesta** según el dueño de cada historia;
se ajusta en el ensayo.

| # | Qué se muestra | Cuenta | Presenta | Evidencia (E2E) |
| --- | --- | --- | --- | --- |
| 1 | **Multi-tenant**: el mismo producto con dos instituciones; cada una ve solo lo suyo (RLS) | `analista.progreso` y `admin.vecina` | Franco | `credit-flow.spec.ts` |
| 2 | **Integraciones**: buró obligatorio, banco y billetera; credenciales propias por fuente en Pro | `admin.vecina` | Franco | `e15-credentials.spec.ts` |
| 3 | **Consentimiento y Perfil 360**: alta del titular `CI-DEMO-0001001`, consentimiento, perfil consolidado | `analista.progreso` | Álvaro | `credit-flow.spec.ts` |
| 4 | **Scoring explicable**: score 0–1000, banda A–E y tres razones (ONNX en la JVM) | `analista.progreso` | Jhon y Nicole | `credit-flow.spec.ts` |
| 5 | **Productos elegibles**: cada producto con sus seis criterios ✓/✗; en Gratis, la invitación al ranking Pro | `analista.progreso` | Jhon | `eligibility-recommendations.spec.ts` |
| 6 | **Ranking Pro**: la misma evaluación en Pro, con monto y plazo sugeridos y sus razones | `admin.vecina` | Jhon | `eligibility-recommendations.spec.ts` |
| 7 | **Decisión y bandeja**: `CI-DEMO-0001002` queda en revisión manual; override justificado que queda en la auditoría | `analista.progreso` | Jhon | `manual-review.spec.ts` |
| 8 | **Planes Gratis/Pro**: Analítica invita a Pro en Gratis; el operador sube el plan y las funciones aparecen sin volver a iniciar sesión | `admin.progreso`, `operador.demo` | Juanjo | `plan-gates.spec.ts`, `plan-upgrade.spec.ts` |
| 9 | **Analítica**: consumo, KPIs y KPIs de cartera (bandas, override, aceptación por producto) | `admin.vecina` | Nicole | `portfolio-kpis.spec.ts` |
| 10 | **Consola del operador**: consumo por tenant y suspensión con confirmación | `operador.demo` | Álvaro | `tenant-suspension.spec.ts` |
| 11 | **API pública**: clave de API y la guía de inicio rápido | `admin.vecina` | Nicole | `docs/guia-inicio-rapido-api.md` |

Si el paso 8 sube a Pro el tenant Gratis, al final se vuelve a bajar desde la
consola del operador para no dejar la demo en otro estado.

## Si algo falla

- **Una fuente no responde**: la evaluación sale **PARCIAL** y queda en revisión.
  Es el comportamiento correcto (nunca se inventa un dato ni se preaprueba): se
  muestra como parte de la demo.
- **La VM o la conexión fallan**: se pasa al video de respaldo, sin improvisar
  sobre otro entorno.

## Video de respaldo

Recorrido completo de la tabla, grabado sobre producción con las mismas cuentas,
sin audio y con subtítulos. Se graba y se edita con el proyecto de video del
equipo (Remotion), en modo claro y a pantalla completa.

## Estado (20 de octubre de 2026)

- [x] Guion paso a paso, cuentas y titulares de demo.
- [ ] Decisión del equipo sobre la prueba en vivo del ingeniero.
- [ ] Ensayo completo sobre producción (requiere las contraseñas de la VPS y el TOTP de los administradores).
- [ ] Video de respaldo grabado.
