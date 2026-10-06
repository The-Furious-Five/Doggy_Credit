# E-07 · Carga k6 de evaluaciones y aislamiento por tenant

Issue: [LOS-106](https://linear.app/los-5-furiosos/issue/LOS-106/e-07-prueba-de-carga-k6-2-h).
Script: [`e2e/k6/credit-assessments.js`](../../e2e/k6/credit-assessments.js).

## Qué mide y cómo decide el resultado

Dos escenarios k6 `constant-arrival-rate` empiezan a la vez, uno Gratis y uno Pro.
El perfil por defecto dura **4 minutos**, con **6 iteraciones por minuto y tenant**:
24 evaluaciones nuevas para cada plan. Cada iteración realiza tres peticiones:

1. `POST /credit-assessments` con consentimiento propio y una Idempotency-Key nueva.
2. `GET /credit-assessments/{id}` con el mismo token, comprobando ID y score.
3. El mismo GET con el token del otro tenant, que debe devolver exactamente 404.

El p95 se calcula únicamente con las duraciones de los POST de carga, incluyendo
respuestas fallidas; no se mezclan login, preparación ni lecturas. Se exige
**p95 < 2000 ms en cada tenant**, éxito 100 %, cero cruces, cero 429, cero
iteraciones perdidas y al menos las 24 muestras por plan. Un tenant lento no se
oculta con el resultado agregado del otro. Un 401/403/429/5xx en la lectura ajena
no demuestra aislamiento; un 2xx cuenta como cruce y hace fallar la prueba.
El 404 debe contener ASSESSMENT_NOT_FOUND y únicamente campos del ProblemDetail:
un error del proxy o un cuerpo con datos de la evaluación tampoco pasa.
En este perfil el buró dispone del informe sintético: una evaluación PARCIAL
o FALLIDA no cuenta como satisfactoria, aunque devuelva HTTP 201.

La preparación obtiene tokens M2M con `client_credentials` de los dos clientes
de la semilla, comprueba los claims y `GET /tenant/actual`, registra o reutiliza
un solicitante por tenant y crea consentimientos independientes para BURO. El
documento sintético es el mismo en ambos tenants, pero sus IDs deben diferir.
También comprueba aislamiento de solicitantes y que un consentimiento ajeno
responda 422 CONSENT_REQUIRED antes de iniciar la carga. Usa el buró WireMock real
incluido en Compose y el scoring del plan; banco y wallet no forman parte de
este perfil de carga. Al terminar revoca los consentimientos que creó.

No se cambia ni desactiva A-07. El límite configurado es 30 peticiones/min para
Gratis y 300 para Pro; cada tenant recibe aproximadamente 18 peticiones/min
durante la carga (incluidas las lecturas ajenas del otro escenario), además de
la preparación. Ejecutar con volúmenes nuevos evita cuotas y buckets usados por
otras pruebas. Este perfil comprueba el SLO con dos tenants concurrentes bajo sus
límites; no mide la capacidad máxima del servidor ni estrés sin límite de plan.

## Ejecución reproducible

Requiere Docker/Compose, Python con PyYAML y **k6 1.8.1**. La versión está fijada
en CI y se usa su formato clásico de resumen. Instalar el binario de la
[release oficial](https://github.com/grafana/k6/releases/tag/v1.8.1) y comprobar
`k6 version`; `run.sh` rechaza otras versiones para conservar el esquema del
reporte. Ejecutar desde la raíz del repositorio; otro Compose no debe ocupar
los puertos 443, 8089–8091 y 3300.

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/requirements.txt
.venv/bin/python scripts/generate_realm.py --origin https://localhost
export COMPOSE_PROJECT_NAME=doggycredit-k6
docker compose --env-file .env.example -f infra/compose.yaml up -d --build --wait --wait-timeout 300
docker compose --env-file .env.example -f infra/compose.yaml cp caddy:/data/caddy/pki/authorities/local/root.crt /tmp/doggycredit-k6-root.crt
curl --fail --cacert /tmp/doggycredit-k6-root.crt https://localhost/actuator/health

set -a
source .env.example
set +a
K6_PYTHON=.venv/bin/python K6_INSECURE_TLS=true bash e2e/k6/run.sh
```

`K6_INSECURE_TLS=true` permite el certificado local de este Compose; el paso
de salud anterior sí verifica la CA. Para un origen con certificado público,
omitir esa opción y definir `K6_BASE_URL` con el origen cuyo realm se configuró.
Los secretos provienen de las variables referenciadas en `seed/tenants.yaml`,
nunca del fixture JSON. Los tokens se renuevan en memoria antes de vencer, y
no se escriben en reportes ni se usan como etiquetas. El fixture se genera
automáticamente y las Idempotency-Key no reutilizan evaluaciones de otra corrida.

`K6_MINUTES` y `K6_ITERATIONS_PER_MINUTE` cambian el perfil; sus valores deben
ser enteros positivos. Si se aumenta la tasa sobre el límite de alguno de los
planes, los 429 hacen fallar la corrida y se registran en el reporte.

El comando conserva el código de salida de k6: un threshold incumplido falla
el proceso. Genera `e2e/k6/results/summary.json` y `e2e/k6/results/result.md`,
con fecha, SHA probado, configuración, muestras, p95 por plan y umbrales. Una
corrida abortada o sin muestras se clasifica INCOMPLETO, nunca PASÓ. Para guardar
el resultado real como evidencia del runbook después de una ejecución:

```bash
cp e2e/k6/results/result.md docs/runbooks/carga-k6-e07-resultados.md
```

Comprobar que ese archivo registra PASÓ y que k6 terminó con código 0; commitear
el resultado y adjuntar el JSON al PR/issue. El reporte conserva fallos para
diagnóstico, pero un reporte FALLÓ/INCOMPLETO no cierra E-07. Eliminar solo los
volúmenes del proyecto de pruebas antes de repetir una corrida limpia:

```bash
COMPOSE_PROJECT_NAME=doggycredit-k6 docker compose --env-file .env.example -f infra/compose.yaml down --volumes --remove-orphans
```

## CI

[`k6-load.yml`](../../.github/workflows/k6-load.yml) corre al cambiar los archivos
de E-07 en PRs/pushes a main y también mediante `workflow_dispatch`. Usa un runner
propio, instala k6 1.8.1, valida los helpers, levanta Compose con credenciales
sintéticas, ejecuta la carga y publica el Markdown en el resumen del job. El
artefacto `e07-k6-resultados` conserva JSON y Markdown por 14 días, incluso si
fallan los umbrales. La limpieza de contenedores/volúmenes siempre se ejecuta.

## Resultado de la preparación local — 2026-10-06

**Estado: pendiente de una medición real contra Compose.** Esta sesión no tiene
el binario k6 instalado (`run.sh` termina con código 127) y Docker devuelve
`permission denied` al conectar con su socket. No se realizaron peticiones de
carga ni se midieron latencias o cruces.

| Verificación | Resultado |
| --- | --- |
| Tests del fixture canónico | 2 tests pasan |
| Tests de clasificación, aislamiento y umbrales | 8 tests Node pasan |
| Salud de https://localhost | Sin servidor accesible en el puerto 443 |
| p95 Gratis / Pro | Sin medición |
| Cruces durante la carga | Sin medición |
| Aceptación E-07 | Pendiente |

Los datos usados por los tests de Node son fixtures unitarios, no resultados
de carga. No se presenta su estado PASÓ como evidencia del servidor. E-07 se
cierra únicamente después de ejecutar k6 y guardar el resultado real en un
runbook.

## Verificaciones del código de la prueba

```bash
.venv/bin/python -m unittest discover -s e2e/k6 -p 'test_*.py'
node --test e2e/k6/support.test.mjs
node --check e2e/k6/credit-assessments.js
bash -n e2e/k6/run.sh
.venv/bin/python e2e/k6/generate_fixture.py
(cd e2e/k6 && k6 inspect credit-assessments.js)
```

Referencias: [escenarios de tasa constante](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/),
[métricas](https://grafana.com/docs/k6/latest/using-k6/metrics/),
[resúmenes personalizados](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/).
