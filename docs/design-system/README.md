# DoggyCredit Design System · v1.0

Este es el contrato obligatorio de toda interfaz de DoggyCredit. Las pantallas
componen componentes; la identidad visual, los estados y las interacciones se
resuelven aquí. Aplica a login, evaluaciones, consentimientos, analytics y futuras
áreas de administración. No modifica los contratos de negocio del backend.

Catálogo interactivo: **`/design-system`**, disponible sin iniciar sesión.
Implementación: `web/src/app/design-system`. API pública: `index.ts`.
El login es la primera pantalla migrada y sirve como ejemplo real.

## Dirección visual

Inspiración: claridad, jerarquía, tipografía nativa y respuesta inmediata de las
[Human Interface Guidelines de Apple](https://developer.apple.com/design/human-interface-guidelines/foundations).
La identidad sigue siendo DoggyCredit: verde profundo del logo, marfil y dorado
cálido. No se redistribuyen tipografías ni iconos propietarios de Apple.

- Priorizar el contenido. Una acción principal por contexto, títulos breves,
  espacio suficiente y sombras discretas.
- Usar superficies opacas para formularios y datos. El material translúcido se
  reserva para navegación; tiene fondo legible y respeta reducción de transparencia.
- Usar movimiento corto como respuesta, nunca como requisito para comprender.
  Respetar `prefers-reduced-motion`; no crear animaciones locales.
- Texto en español, lenguaje directo, sin culpar al usuario. Dar el siguiente paso
  cuando una operación falla. No prometer aprobación de crédito por un color.
- No convertir este estilo en botones sin etiquetas, tipografía diminuta o
  superficies con poco contraste. La calma visual debe facilitar la tarea.

## Fundamentos

`_tokens.scss` define valores y temas; `_breakpoints.scss` define los mixins de
layout. `_foundation.scss` establece tipografía y utilidades. `_components.scss`
resuelve presentación y estados. `styles.scss` es la única entrada global.

| Familia | Tokens / uso |
| --- | --- |
| Superficies | `--ds-bg`, `--ds-surface`, `--ds-surface-muted`, `--ds-glass` |
| Texto | `--ds-text`, `--ds-muted`, `--ds-on-primary` |
| Acción | `--ds-primary`, `--ds-primary-hover`, `--ds-focus` |
| Estados | `--ds-success`, `--ds-warning`, `--ds-danger`, `--ds-info` y sus `-bg` |
| Tipografía | `--ds-font`, `--ds-mono`, `--ds-text-xs` a `--ds-text-xl`, `--ds-title`, `--ds-display` |
| Espaciado | `--ds-space-1/2/3/4/5/6/8/10/12/16/20/24`; base de 4 px |
| Forma | `--ds-radius-sm/md/lg/pill`, `--ds-border-width` |
| Profundidad | `--ds-shadow`, `--ds-shadow-raised`, `--ds-z-sticky/toast` |
| Movimiento | `--ds-duration-fast/duration/slow`, `--ds-ease` |
| Layout | `--ds-content-width`, `--ds-reading-width`, `--ds-form-width` |

Los nombres describen intención. Nunca usar el color verde para representar
éxito directamente: usar `success`, que puede cambiar por tema. Las constantes
estructurales de los componentes (por ejemplo, una región oculta para lectores)
se mantienen dentro del sistema, nunca se replican en las pantallas.

Tema claro, oscuro y automático mediante `DsThemeService.preference.set(...)`.
La preferencia es local al navegador; no se almacena junto a datos del usuario.
No cambiar manualmente atributos de tema desde las pantallas.

## API de componentes

Todos son standalone. Importar exclusivamente desde `../design-system` (ajustar
la ruta), nunca desde sus archivos internos.

| API | Contrato |
| --- | --- |
| `button[dsButton]` | `variant`: primary/secondary/quiet/danger; `busy`, `disabled`, `full`; `type` por defecto button. Busy bloquea clics y expone aria-busy. |
| `a[dsLink]` | Navegación real con href o routerLink. No usar botones para navegar. |
| `ds-card` | Superficie no interactiva. `padding`: normal/compact. No convertir toda la tarjeta en un botón implícito. |
| `ds-badge` | `tone`: neutral/success/warning/danger/info. Siempre texto, nunca solo color. |
| `ds-alert` | `heading` obligatorio, contenido proyectado y `tone`. `urgent` activa role=alert; por defecto role=status. |
| `ds-field` | `label` obligatorio; `hint`, `error`, `required`, `controlId` opcional. Un control por campo. Genera y conecta label, ayuda y error. |
| `input[dsInput]`, `textarea[dsInput]`, `select[dsSelect]` | Controles nativos compatibles con Forms y Reactive Forms. Dentro de ds-field heredan id, required, aria-invalid y aria-describedby. Fuera requieren etiqueta/id explícitos. |
| `input[dsCheckbox]` | Declarar **type="checkbox"**; envolver en label.ds-choice. Compatible con ngModel/formControl. |
| `input[dsRadio]` | Declarar **type="radio"** y name común; envolver en label. Se puede usar dentro de fieldset.ds-segmented con legend. |
| `ds-icon` | Iconos decorativos originales centralizados: arrow/check/close/plus/shield/search/chevron/moon. `name` tipado. El control contenedor debe tener nombre accesible. |
| `ds-spinner` | `label` descriptivo; role=status. No ocultar toda la pantalla por una operación local. |
| `ds-skeleton` | Placeholder decorativo, aria-hidden; acompañar una región con estado de carga legible. |
| `ds-empty-state` | `heading` y `description`; proyectar una acción útil, si corresponde. |
| `ds-stat` | `label`, `value`, `detail`; no calcula ni interpreta datos financieros. |
| `table[dsTable]` | Tabla nativa con caption y encabezados scope. Contenedor ds-table-scroll con nombre y tabindex=0. |
| `ds-dialog` | `heading` obligatorio; `show()`, `close()`, evento `dismissed`. Dialog nativo: modal, Escape, foco contenido y restauración al origen. |
| `DsToastService` + `ds-toast-outlet` | `show(message)`, `dismiss()`. Un outlet global. Aviso no crítico por 8 s; pausa al interactuar. Errores importantes deben permanecer en ds-alert. |
| `ds-brand` | Logo y wordmark; el enlace externo aporta nombre accesible cuando se usa para navegar. |

Utilidades: `ds-container`, `ds-stack`, `ds-stack-sm`, `ds-row`, `ds-between`,
`ds-grid`, `ds-reading`, `ds-muted`, `ds-caption`, `ds-eyebrow`, `ds-display`,
`ds-mono`, `ds-divider`, `ds-sr-only` y `ds-skip`.

## Receta: un formulario

```ts
import { DsButton, DsField, DsInput } from '../design-system';
// Agregar los componentes y ReactiveFormsModule/FormsModule a imports.
```

```html
<form (ngSubmit)="save()" class="ds-stack">
  <ds-field label="Documento" hint="Usa un documento ficticio"
            [error]="documentError()" required>
    <input dsInput name="document" [(ngModel)]="document" />
  </ds-field>
  <button dsButton type="submit" [busy]="saving()">Guardar</button>
</form>
```

Conservar los valores ante fallos de red. Validar al salir del campo o al enviar;
no mostrar errores antes de que la persona interactúe. En errores de envío,
llevar el foco al primer campo inválido. `busy` protege la interacción, pero la
idempotencia real sigue siendo responsabilidad de la API.

Para agrupar radios usar fieldset/legend. Un switch o segmented control no es
una pestaña: los ejemplos usan selección nativa, con flechas y semántica de radio.
Para navegación utilizar enlaces y `aria-current="page"`, no roles inventados.

## Receta: layout propio, lenguaje visual compartido

```scss
@use '../design-system/breakpoints' as screen;
.page-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ds-space-6);
}
@include screen.compact {
  .page-layout { grid-template-columns: 1fr; }
}
```

Las pantallas pueden organizar el contenido. No deben redefinir botones, inputs,
colores o tamaños visuales con valores literales. No usar ::ng-deep, !important,
ngStyle, innerHTML, CSS global nuevo ni imports internos para esquivar el sistema.

## Patrones para el producto

- **Acceso:** explicar a dónde lleva el botón; credenciales solo en Keycloak.
- **Consentimiento:** texto de finalidad y alcance antes de confirmar. Nada
  preseleccionado. Separar permisos obligatorios de los opcionales.
- **Evaluación:** mostrar estado escrito, fuentes y fecha; Datos simulados siempre
  visible donde corresponda. Un score nunca reemplaza una explicación.
- **Listado:** título, acción principal, filtros etiquetados, estado vacío y error
  con reintento. Conservar encabezados al permitir desplazamiento horizontal.
- **Acción irreversible:** diálogo con consecuencias concretas; acción de cancelar
  clara y nombre específico del botón de confirmación.
- **Carga:** mantener contexto y bloquear solo las acciones dependientes.
- **Error:** decir qué ocurrió, qué se conserva y qué se puede hacer.

## Control obligatorio y evolución

```bash
cd web
npm run ds:check  # arquitectura visual
npm run ds:test   # demuestra que el control detecta divergencias
npm test -- --watch=false
npm run build    # ejecuta ds:check antes de compilar
```

`prestart`, `prewatch` y `prebuild` ejecutan el control; CI también lo ejecuta
explícitamente. Revisa plantillas Angular externas e inline con el parser de
Angular, y estilos CSS/SCSS con Sass + PostCSS. Rechaza controles sin directivas,
estilos inline, valores visuales locales, tokens desconocidos, escapes de
encapsulación e imports internos. `app/api` se excluye porque es código generado.
El guardrail ayuda a evitar divergencias habituales; no sustituye revisión humana
ni es una barrera frente a cambios deliberados en la propia configuración.

Para hacer obligatorios los checks al fusionar, la administración del repositorio
debe marcar el job **web** como requerido en la protección de la rama. El código
no puede imponer esa configuración remota por sí solo.

Proceso para extender:

1. Revisar catálogo y API; reutilizar antes de proponer otra variante.
2. Si falta una capacidad, agregarla al Design System sin dependencias de negocio.
3. Documentar API, estados, accesibilidad y ejemplos en el catálogo.
4. Añadir pruebas de comportamiento cuando exista interacción nueva; comprobar
   tema oscuro, teclado, móvil, zoom y movimiento reducido.
5. Actualizar esta documentación y el changelog en el mismo cambio.
6. Migrar consumidores si hay ruptura. Nunca crear una excepción local silenciosa.

El directorio design-system solo contiene fundamentos y componentes genéricos.
Las páginas, permisos, HTTP, scoring y DTOs viven fuera. Las reglas también se
registran en `AGENTS.md` para asistentes de desarrollo.

## Verificación visual antes de integrar

- 375 px, 768 px y 1440 px; zoom al 200 %, títulos largos y sin scroll horizontal
  de la página (solo tablas/código pueden desplazarse).
- Temas claro/oscuro; los textos normales deben alcanzar contraste 4.5:1.
- Recorrer con Tab/Shift+Tab; activar con teclado; abrir diálogo, recorrerlo y
  cerrar con Escape; el foco vuelve a su origen.
- Probar lector de pantalla para etiquetas, errores, carga y notificaciones.
- Activar reducción de movimiento y transparencia en el sistema operativo.
- Ejecutar formularios válidos/invalidos y estados de carga/deshabilitado.

Las pruebas automáticas de DOM no equivalen a auditoría completa WCAG. En esta
sesión el sandbox impide abrir un servidor HTTP local; la revisión visual real
en navegador queda pendiente y no se reporta como aprobada.
