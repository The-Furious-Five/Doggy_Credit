# E-07 · Prueba de carga de evaluaciones

Ejecutar desde la raíz: `bash e2e/k6/run.sh`, con Compose levantado y las
variables M2M de `.env.example` cargadas. Requiere k6 1.8.1 y Python/PyYAML.

El [runbook](../../docs/runbooks/carga-k6-e07.md) explica preparación, tasas,
umbrales, aislamiento, ejecución en CI y registro de los resultados reales.
El fixture y los reportes se generan en `.generated/` y `results/`, ignorados
por Git; no contienen contraseñas ni tokens.
