# Resumen y Analítica (E-05 / LOS-104)

Las rutas `/app/resumen` y `/app/analitica` comparten `AnalyticsDashboard`.
Los datos llegan mediante los clientes generados en `web/src/app/api/`:

- E-02: `GET /analytics/resumen?desde=&hasta=` proporciona el plan, los KPIs,
  las necesidades y, si está habilitada, la analítica avanzada.
- C-09: `GET /credit-assessments?pagina=1&tamano=5` proporciona las evaluaciones
  recientes del Resumen, su decisión y el enlace al perfil del cliente.
- A-01: el shell consulta `GET /tenant/actual` y `/tenant/actual/uso` para mostrar
  la organización, el plan y su uso mensual. El dashboard usa el plan y la
  capacidad `analiticaAvanzada` incluidos en la respuesta de E-02, del mismo
  catálogo del servidor, sin duplicar la consulta del shell.

El JWT identifica al tenant; las vistas no envían IDs de tenant en las consultas.
No importan `data.ts` ni muestran cifras financieras sin una fuente. «Mora a
90 días» permanece fuera de ambas vistas (DEBT-008).

## Comportamiento

- Gratis conserva los cuatro KPIs básicos y las necesidades, con el aviso Pro.
  Pro y Enterprise muestran los indicadores avanzados que habilita el servidor.
- Si falta `pro` en una respuesta con `analiticaAvanzada=true`, se conservan los
  KPIs básicos y se ofrece reintentar; no se ofrece mejorar un plan que ya incluye
  la funcionalidad.
- Una lista de necesidades vacía o con todas las cantidades en cero muestra
  «Sin necesidades detectadas». No se presenta una distribución ficticia.
- Un score o tiempo desconocido se muestra como `—`. El cero se conserva como
  cero. Las distribuciones usan `DsProgress`, con etiquetas accesibles.
- KPIs y evaluaciones recientes cargan y fallan de manera independiente, con
  reintento en cada sección. Un error HTTP conserva el período aplicado.
- Aplicar otro período cancela la consulta anterior; Actualizar vuelve a consultar
  el período aplicado, aunque se hayan editado fechas sin enviarlas. El período
  sólo filtra los KPIs; «Evaluaciones recientes» conserva las últimas del tenant.
- El tema y el movimiento reducido se resuelven con los componentes, tokens y
  reglas globales del Design System; no hay animaciones locales.

## Verificación

Desde `web/`:

```bash
npm run ds:check
npm run ds:test
npm test -- --watch=false
npm run build
```

En macOS, si falla el cache LMDB del CLI, usar `NG_BUILD_CACHE_STORE=sqlite`
para `npm test` y `npm run build`, como se documenta en el runbook de Sprint 1.

`analytics-dashboard.spec.ts` cubre estados, formato, enmascaramiento y filtros.
`analytics-views.spec.ts` recorre las rutas reales con el shell, los clientes HTTP
generados y los interceptores de JWT y errores. Comprueba Gratis/Pro en ambos
temas, Enterprise, evaluaciones recientes, carga/error/vacío, cancelación y
reintento, sin necesitar un backend para estas pruebas.

Para revisar en el navegador con el stack real, entrar con un tenant Gratis y
uno Pro, abrir ambas rutas, cambiar el tema desde el shell y comprobar fechas,
distribuciones y enlaces usando Tab/Enter. Activar la preferencia de movimiento
reducido del sistema; la carga debe seguir siendo comprensible sin animación.
