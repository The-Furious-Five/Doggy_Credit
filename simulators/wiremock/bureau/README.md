# Simulador del buró (WireMock)

`mappings/informe-demo.json` es un **stub inicial** (un solo titular fijo),
solo para que `docker compose up` responda algo desde el Día 1.

El generador sintético completo (~50 personas, con documentos coherentes
entre buró/banco/billetera) es el **SPIKE-4** del Sprint 1 (Alvaro, con
Nicole) — ver el capítulo 9.2 de la guía de inducción. Ese generador debe
escribir sus mappings aquí mismo, siguiendo el mismo formato.

Prueba rápida una vez levantado el contenedor:

```bash
curl http://localhost:8089/bureau/v1/informe/CI-DEMO-0001001
```
