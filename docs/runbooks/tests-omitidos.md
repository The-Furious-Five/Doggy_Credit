# Tests omitidos: lista blanca con vencimiento

En CI, un test omitido (`@Disabled`, `assumeTrue` fallido, Testcontainers
con `disabledWithoutDocker`, etc.) **rompe el pipeline** salvo que esté en
[`ci/skipped-tests-allowlist.json`](../../ci/skipped-tests-allowlist.json).
Contar un test omitido como aprobado es deuda que no se acepta
(ver `docs/debt-register.md`). También falla si no corrió ningún test.

## Agregar una excepción temporal

```json
[
  {
    "test": "com.doggycredit.consent.ConsentFlowTest#revocaConsentimiento",
    "reason": "Depende del simulador del buró (SPIKE-3), aún no mergeado",
    "owner": "Alvaro",
    "expires": "2026-10-10"
  }
]
```

- `test`: `Clase#metodo` (nombre completo), o solo `Clase` para todos sus métodos.
- Los cuatro campos son obligatorios. `expires` es una fecha ISO (`AAAA-MM-DD`).
- Al vencer, el gate falla: se arregla el test o se renueva con un motivo nuevo
  en el PR. No hay excepciones sin fecha.

## Probar localmente

```bash
cd backend && ./mvnw verify && cd ..
python ci/check_skipped_tests.py            # contra backend/target/surefire-reports
python ci/check_skipped_tests.py --self-test
```

Docker tiene que estar corriendo: el CI corre `docker info` antes de los
tests y falla si no está.
