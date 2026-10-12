# Bandeja de revisión manual (E-10 / LOS-125)

RF-DEC-03, parte de UI. En `/app/revision` (menú Operación › Revisión manual)
están los casos que las políticas dejaron para una persona. Cada caso se
resuelve con una decisión, una justificación obligatoria y, al aprobar, el
producto elegido.

E-10 depende de **D-10** (contrato, LOS-110) y **C-10** (backend, LOS-111).
Para que E-10 quede lista sin esperarlas, este PR trae la **propuesta del
contrato DEC-03** y el cliente Angular regenerado desde ella. La vista ya
consume ese cliente generado.

## Contrato propuesto (tag `revision-manual`)

| Operación | Ruta | Uso |
| --- | --- | --- |
| `listarCasosRevision` | `GET /revision-manual?pagina&tamano` | `PaginaCasosRevision`: `items` (`CasoRevision`), `total` y `productos` activos del tenant |
| `resolverCasoRevision` | `POST /revision-manual/{evaluacionId}/resolucion` | `ResolucionNueva` → `Resolucion` |

- `ResolucionNueva`: `decision` (`APROBADO` / `RECHAZADO`), `justificacion`
  (10 a 1000 caracteres, obligatoria) y `productoId` (obligatorio solo al aprobar).
- `PaginaCasosRevision.productos` existe porque `ANALYST` no puede leer `/productos`
  (solo `TENANT_ADMIN`), pero necesita elegir el producto.
- Errores del catálogo existente, sin códigos nuevos: 400 `VALIDATION_ERROR`,
  403 `INSUFFICIENT_ROLE`, 404 `ASSESSMENT_NOT_FOUND` / `PRODUCT_NOT_FOUND` y
  409 `CONCURRENT_UPDATE` (otra persona ya lo resolvió).
- Al ir en un **tag propio**, el backend genera `RevisionManualApi` aparte. Con
  `skipDefaultInterface`, agregar estas rutas a `EvaluacionesApi` habría roto la
  compilación de `AssessmentController` hasta que existiera C-10. Así, el
  backend compila igual y las rutas responden 404 mientras nadie implemente la
  interfaz.

## Integrar

1. **D-10** adopta este fragmento tal cual o lo ajusta. Si cambia algo, se
   regenera el cliente (`npm run generate:api`) y solo hay que adaptar
   `console/manual-review.ts`: la vista toca el contrato solo a través de
   `RevisionManualService` y sus modelos.
2. **C-10** implementa `RevisionManualApi` en el módulo `assessment`:
   - listar las evaluaciones del tenant en `REVISION_MANUAL`, de la más antigua
     a la más reciente, con los productos activos;
   - resolver con justificación obligatoria y producto activo del tenant al aprobar;
   - responder 409 si el caso ya no está pendiente;
   - registrar cada override en `audit.access_log` con el `X-Correlation-Id`;
   - permitir el acceso a `ANALYST` y `TENANT_ADMIN`.
3. Se fusiona E-10. E-12 (LOS-126) agrega el escenario E2E sobre el backend real.

## Comportamiento de la vista

- Estados de carga, error con reintento, rol sin permiso (403, sin toast
  duplicado) y bandeja vacía. Ningún dato es fijo: todo sale de la API.
- El documento y los montos respetan el enmascarado (`SensitiveDataService`).
- El diálogo muestra score, banda, solicitud, destino, resultado parcial y las
  3 razones principales. Nada viene preseleccionado.
- **No se envía nada** sin decisión, sin producto al aprobar, o con una
  justificación vacía o de menos de 10 caracteres. El error aparece en el campo
  (`aria-invalid`) y el foco va al primer campo inválido.
- Al resolver se muestra un toast («Caso aprobado con Microcrédito.»), la bandeja
  se recarga y el foco pasa al título, porque la fila que abrió el diálogo ya no
  existe. Si era el único caso de la última página, se vuelve a la anterior.
- 409 o 404 del caso: «Este caso ya no está pendiente» y «Volver a la bandeja».
  Producto desactivado: se pide otro con la lista actualizada. Error de red: se
  conservan la decisión y la justificación.

## Verificación

- `manual-review.spec.ts` (prueba de componente) usa el servicio generado con
  `HttpTestingController`, así que comprueba rutas, parámetros y cuerpo exactos
  del contrato.
- En el navegador, contra el Compose completo (login, shell, JWT e interceptores
  reales), se respondió solo `/revision-manual` con datos del contrato. Se
  comprobaron el teclado, el foco y los errores, y axe no encontró violaciones
  en tema claro ni oscuro. A 390 px no hay desborde.
- Al revisar en móvil aparecieron dos defectos del Design System, corregidos en
  la 3.1.1 (ver `docs/design-system/CHANGELOG.md`): `.ds-table-scroll` no
  contenía el encabezado `.ds-sr-only`, y `ds-tabs` crecía con tablas anchas.
