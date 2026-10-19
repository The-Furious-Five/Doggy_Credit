# Contrato de elegibilidad y recomendaciones (E-16 / LOS-139)

RF-REC-02 y RF-REC-03 amplían el contrato de C-06 (PR #43). Se publican en
su propio PR, después de integrar C-06 y antes de implementar el backend.
La fuente de verdad es `contracts/openapi/doggycredit-api.v1.yaml`; Java y
Angular se generan con las herramientas existentes. La interfaz de C-16
consume estos tipos, sin definir respuestas alternativas.

## Elegibilidad en todos los planes

Cada evaluación nueva devuelve `elegibilidad[]`, un snapshot de los productos
activos del tenant, con `productoId`, `tipoProducto`, `moneda`, `elegible` y
seis `criterios`, cada uno con `codigo`, `cumple` y `detalle`:

| Código | Comprobación |
| --- | --- |
| `BANDA_MINIMA` | La banda del titular alcanza la mínima del producto. |
| `RANGO_MONTO` | El monto propuesto está entre el mínimo y el máximo y usa la misma moneda. |
| `RANGO_PLAZO` | El plazo de la necesidad está dentro del rango del producto. |
| `TIPO_TITULAR` | PYME requiere empresa; microcrédito requiere persona; VERDE admite ambas. |
| `DESTINO` | VERDE requiere eficiencia energética. |
| `CAPACIDAD_PAGO` | La cuota del monto y plazo propuestos, a la tasa del producto, no supera el máximo de RF-NEC-01. |

El monto propuesto es el menor entre el sugerido por RF-NEC-01, el máximo del
producto y el valor presente de la cuota máxima a su tasa y plazo. Se redondea
hacia abajo a dos decimales. Si queda por debajo del mínimo, el producto no
califica. El plazo es el solicitado o, si no se indicó, el predeterminado de
la necesidad; estar fuera del rango produce un criterio incumplido.

Un producto de otra moneda no califica: no se hace conversión de divisas.
Con fuentes fallidas, el criterio de capacidad no se da por satisfecho y el
detalle indica que no pudo verificarse; se conserva la regla de C-06 de no
emitir recomendaciones para un resultado PARCIAL.

`elegible` es la conjunción de los seis criterios. Un producto inactivo no
aparece en un snapshot nuevo. Que se desactive después no altera el resultado
histórico que vio el analista. Una lista vacía significa que no había productos
activos; una lista de productos con `elegible:false` significa que ninguno aplica.

## Ranking en PRO y ENTERPRISE

Los planes con `Feature.RECOMENDACIONES` reciben además `recomendaciones[]`
ordenadas por ajuste: primero el destino verde cuando corresponde y después
la cercanía del monto al centro del rango, con desempate por tipo de producto.
Cada recomendación nueva contiene `montoSugerido`, `plazoSugeridoMeses` y al
menos dos `razones`. El monto y el plazo respetan los rangos y la capacidad;
solo se recomiendan productos que cumplen los criterios.

FREE recibe la elegibilidad y el campo `recomendaciones` queda ausente.
La lectura y la repetición idempotente también consultan el plan vigente:
una bajada a FREE oculta el ranking histórico, sin borrar su snapshot.
Una subida no inventa un ranking para evaluaciones FREE que no lo calcularon.

`razon` se conserva como texto principal compatible con los consumidores de
C-06. `plazoSugeridoMeses`, `razones` y `elegibilidad` se agregan como opcionales:
un snapshot anterior a E-16 conserva sus datos originales, sin recalcular ni
fabricar criterios, plazos o razones adicionales. Un ranking evaluado sin
productos elegibles sigue siendo `[]`; no disponible sigue siendo un campo
ausente. Los valores monetarios se representan como cadenas decimales.

## Verificación del contrato

```bash
bash scripts/generate_clients.sh
cd backend && ./mvnw -B -DskipTests compile
```

El job Contrato API verifica que los archivos generados coincidan con el
contrato y ejecuta `oasdiff breaking` contra la base del PR. Los campos
existentes no se eliminan ni pasan a ser obligatorios. No se agregan operaciones
a interfaces generadas existentes; los controladores conservan su compilación
antes de implementar la nueva respuesta.
