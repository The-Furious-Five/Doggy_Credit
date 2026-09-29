# DoggyCredit Design System · v2.0

Este es el contrato obligatorio de toda interfaz de DoggyCredit. Las pantallas
componen componentes; la identidad visual, los estados y las interacciones se
resuelven aquí. Aplica a login, evaluaciones, consentimientos, analytics y futuras
áreas de administración. No modifica los contratos de negocio del backend.

Catálogo interactivo: **`/design-system`**, disponible sin iniciar sesión.
Implementación: `web/src/app/design-system`. API pública: `index.ts`.
El login es la primera pantalla migrada y sirve como ejemplo real.

## Dirección visual

**Sólido, estable, impecable.** Inspiración: claridad, jerarquía, tipografía
nativa y respuesta inmediata de las
[Human Interface Guidelines de Apple](https://developer.apple.com/design/human-interface-guidelines/foundations).
La identidad es DoggyCredit: los colores exactos del logo. No se redistribuyen
tipografías ni iconos propietarios de Apple.

- **Colores sólidos y precisos.** Sin degradados, neones, brillos, desenfoques
  ni tintes acuarelados. El checker rechaza `gradient()`, `filter`,
  `backdrop-filter` y `mix-blend-mode` en las pantallas.
- **El color es una señal, no un fondo.** Las superficies son neutras y opacas;
  el color aparece en la acción principal, el punto de un badge o el icono de una
  alerta. Nunca un rectángulo teñido.
- Priorizar el contenido. Una acción principal por contexto, títulos breves,
  espacio suficiente y sombras apenas perceptibles.
- Todas las superficies son opacas, incluida la navegación.
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

### Paleta de marca

Valores tomados literalmente de `public/doggycredit.svg`; idénticos en todos los
temas. Se usan para la marca y la documentación; las pantallas consumen roles.

| Token | Nombre | Valor | Uso en el logo |
| --- | --- | --- | --- |
| `--ds-brand-pine` | Pino | `#10403C` | "Doggy", flecha, superficie de marca |
| `--ds-brand-forest` | Bosque | `#1A5149` | Onda del emblema; acción principal en claro |
| `--ds-brand-sage` | Salvia | `#377766` | Barras del gráfico |
| `--ds-brand-honey` | Miel | `#D59132` | "Credit"; acento |
| `--ds-brand-sand` | Arena | `#F7DDA0` | Pelaje claro; selección, acción sobre pino |
| `--ds-brand-ivory` | Marfil | `#FFFEFD` | Fondo del logo; superficies |
| `--ds-brand-cocoa` | Cacao | `#261F14` | Contornos |

### Roles semánticos

| Familia | Tokens / uso |
| --- | --- |
| Superficies | `--ds-bg`, `--ds-surface`, `--ds-surface-muted`, `--ds-surface-strong`, `--ds-surface-raised`, `--ds-inverse` |
| Texto | `--ds-text`, `--ds-muted`, `--ds-link`, `--ds-accent-text`, `--ds-on-primary`, `--ds-on-inverse` |
| Acción | `--ds-primary`, `--ds-primary-hover`, `--ds-focus`, `--ds-accent` |
| Estados | `--ds-success`, `--ds-warning`, `--ds-danger`, `--ds-info` (texto y señales; sin fondos teñidos) |
| Bordes | `--ds-border` (separadores), `--ds-control-border` (3:1), `--ds-hairline`, `--ds-border-width` |
| Tipografía | `--ds-font`, `--ds-font-display`, `--ds-mono`, `--ds-text-xs` a `--ds-text-xl`, `--ds-title`, `--ds-display`, `--ds-display-fit` |
| Espaciado | `--ds-inset`, `--ds-space-1/2/3/4/5/6/8/10/12/16/20/24`; base de 4 px |
| Forma | `--ds-radius-xs/sm/md/lg/xl/pill`; controles en md, tarjetas en lg |
| Profundidad | `--ds-shadow-sm`, `--ds-shadow`, `--ds-shadow-raised`, `--ds-backdrop`, `--ds-z-sticky/toast` |
| Movimiento | `--ds-duration-fast/duration/slow`, `--ds-ease`, `--ds-press-scale` |
| Layout | `--ds-content-width`, `--ds-reading-width`, `--ds-form-width`, `--ds-dialog-width`, `--ds-sidebar-width` |

Los nombres describen intención. Nunca usar el color verde para representar
éxito directamente: usar `success`, que puede cambiar por tema. Las constantes
estructurales de los componentes (por ejemplo, una región oculta para lectores)
se mantienen dentro del sistema, nunca se replican en las pantallas.

`--ds-display-fit` ajusta el titular al ancho de su columna: el contenedor
declara `container-type: inline-size` (ver el panel de marca del login).

### Superficie de marca

`.ds-brand-surface` pinta un área en verde pino sólido y reasigna todos los
roles (texto marfil, acción arena, acento miel, estados aclarados) con contraste
AA verificado. Cualquier componente interior funciona sin estilos propios. Usarla
para momentos de marca (acceso, bienvenida), no para formularios extensos.

Tema claro, oscuro y automático mediante `DsThemeService.preference.set(...)`.
En oscuro el verde de marca se aclara lo justo para seguir siendo legible sobre
fondos casi negros; no se usan tonos pastel ni neón.
La preferencia es local al navegador; no se almacena junto a datos del usuario.
No cambiar manualmente atributos de tema desde las pantallas.

## API de componentes

Todos son standalone. Importar exclusivamente desde `../design-system` (ajustar
la ruta), nunca desde sus archivos internos.

| API | Contrato |
| --- | --- |
| `button[dsButton]` | `variant`: primary/secondary/quiet/danger; `busy`, `disabled`, `full`, `icon`; `type` por defecto button. Busy bloquea clics y expone aria-busy. `icon` crea un botón circular solo con icono y exige `aria-label`. Danger es texto rojo sobre superficie neutra. |
| `a[dsLink]` | Navegación real con href o routerLink. No usar botones para navegar. |
| `ds-card` | Superficie no interactiva. `padding`: normal/compact. No convertir toda la tarjeta en un botón implícito. |
| `ds-badge` | `tone`: neutral/success/warning/danger/info. Texto neutro con un punto sólido del tono (neutral sin punto). Siempre texto, nunca solo color. |
| `ds-alert` | `heading` obligatorio, contenido proyectado y `tone`. Superficie neutra con icono del tono. `urgent` activa role=alert; por defecto role=status. |
| `ds-field` | `label` obligatorio; `hint`, `error`, `required`, `controlId` opcional. Un control por campo. Genera y conecta label, ayuda y error (con icono). |
| `input[dsInput]`, `textarea[dsInput]`, `select[dsSelect]` | Controles nativos compatibles con Forms y Reactive Forms. Dentro de ds-field heredan id, required, aria-invalid y aria-describedby. Fuera requieren etiqueta/id explícitos. |
| `input[dsCheckbox]` | Declarar **type="checkbox"**; envolver en label.ds-choice. Compatible con ngModel/formControl. Dibujo propio; en `forced-colors` vuelve al control nativo. |
| `input[dsRadio]` | Declarar **type="radio"** y name común; envolver en label. Se puede usar dentro de fieldset.ds-segmented con legend. |
| `ds-icon` | Iconos decorativos originales centralizados: arrow/check/close/plus/shield/search/chevron/moon/info/success/warning/danger/tray/building/flask. `name` tipado. El control contenedor debe tener nombre accesible. |
| `ds-spinner` | `label` descriptivo; role=status. No ocultar toda la pantalla por una operación local. |
| `ds-skeleton` | Placeholder decorativo, aria-hidden; acompañar una región con estado de carga legible. |
| `ds-empty-state` | `heading`, `description` e `icon` opcional (tray); proyectar una acción útil, si corresponde. |
| `ds-stat` | `label`, `value`, `detail`; no calcula ni interpreta datos financieros. |
| `table[dsTable]` | Tabla nativa con caption y encabezados scope. Contenedor ds-table-scroll con nombre y tabindex=0. |
| `ds-dialog` | `heading` obligatorio; `show()`, `close()`, evento `dismissed`. Dialog nativo: modal, Escape, foco contenido y restauración al origen. |
| `DsToastService` + `ds-toast-outlet` | `show(message)`, `dismiss()`. Un outlet global. Aviso no crítico por 8 s; pausa al interactuar. Errores importantes deben permanecer en ds-alert. |
| `ds-brand` | Emblema sobre placa marfil + wordmark en los colores exactos del logo (Pino/Miel; marfil sobre fondos oscuros). El enlace externo aporta nombre accesible cuando se usa para navegar. |
| `ds-logo` | Emblema como icono de aplicación (placa marfil 72 px). Decorativo por defecto; `label` le da nombre accesible. |
| `ds-swatch` | Muestra decorativa de un token de color. `color` tipado (`DsColorToken`), `shape`: tile/dot. Acompañar siempre con el nombre en texto. |

Utilidades: `ds-container`, `ds-stack`, `ds-stack-sm`, `ds-row`, `ds-between`,
`ds-grid`, `ds-reading`, `ds-actions`, `ds-muted`, `ds-caption`, `ds-eyebrow`
(texto miel, sin mayúsculas forzadas), `ds-lead`, `ds-display`, `ds-mono`,
`ds-divider`, `ds-brand-surface`, `ds-sr-only` y `ds-skip`.

`ds-actions` agrupa las acciones al pie de un formulario o diálogo: la última es
la principal; si no caben en una línea se apilan a ancho completo con la
principal arriba y la de cancelar abajo.

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
Tampoco degradados, filtros ni desenfoques, aunque se compongan con tokens.

## Patrones para el producto

- **Acceso:** explicar a dónde lleva el botón; credenciales solo en Keycloak.
- **Consentimiento:** texto de finalidad y alcance antes de confirmar. Nada
  preseleccionado. Separar permisos obligatorios de los opcionales.
- **Evaluación:** mostrar estado escrito, fuentes y fecha; Datos simulados siempre
  visible donde corresponda. Un score nunca reemplaza una explicación.
- **Listado:** título, acción principal, filtros etiquetados, estado vacío y error
  con reintento. Conservar encabezados al permitir desplazamiento horizontal.
- **Acción irreversible:** diálogo con consecuencias concretas; acción de cancelar
  clara y nombre específico del botón de confirmación, agrupadas en `ds-actions`.
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
encapsulación, imports internos, degradados y desenfoques. `app/api` se excluye
porque es código generado. `ds:test` además verifica que el propio sistema no
define degradados ni desenfoques y que los roles de los tres contextos (claro,
oscuro y superficie de marca) alcanzan 4.5:1 en texto y 3:1 en bordes y foco.
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
- Activar reducción de movimiento y contraste aumentado en el sistema operativo.
- Ejecutar formularios válidos/invalidos y estados de carga/deshabilitado.

Las pruebas automáticas de DOM no equivalen a auditoría completa WCAG. La v2 se
revisó en Chrome headless (375, 768, 1024 y 1440 px sin scroll horizontal; claro
y oscuro; recorrido con Tab, diálogo con Escape y retorno de foco, errores de
formulario y notificación). Siguen pendientes: lector de pantalla, zoom al 200 %
y Safari/Firefox.
