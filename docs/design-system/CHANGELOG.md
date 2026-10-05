# Changelog

## 3.1.0 — 2026-10-01

- `a[dsLink]` acepta `appearance` (primary/secondary/quiet) para presentar como
  botón un enlace que cambia de página (CTA de landing, "Nueva evaluación").
- Iconos nuevos: grid, user, users, chart, plug, code, sliders, bolt, spark,
  leaf, store, coin, wallet, database, lock, key, sun, logout.

## 3.0.0 — 2026-09-29

Controles propios: ningún control de la interfaz depende de la apariencia del
navegador.

- Nuevos: `ds-select` (simple y múltiple), `ds-combobox` (búsqueda con lista),
  `ds-date-picker` y `ds-calendar` (en español, semana desde el lunes),
  `ds-number-field` (−/+ propios, prefijo y sufijo), `ds-switch`, `ds-menu`,
  `ds-tabs`/`ds-tab`, `ds-pagination`, `ds-steps` y `ds-progress`. Todos con
  patrones ARIA, teclado completo, temas, contraste aumentado y movimiento reducido.
- Paneles flotantes con posición fija: no se recortan en diálogos ni tablas y
  cambian de lado si no caben. Escape cierra primero el panel y luego el diálogo.
- `ds-field`: la etiqueta tiene id y lleva el foco también a controles propios.
- Iconos nuevos: calendar, minus, more, chevron-down, chevron-left.
- Tokens nuevos: `--ds-z-popover`, `--ds-popover-min-width`, `--ds-switch-width`,
  `--ds-switch-height`, `--ds-progress-height`, `--ds-indicator`.
- **Ruptura:** se elimina `select[dsSelect]` y el token `--ds-chevron`; migrar a
  `ds-select`. El checker rechaza en pantallas `<select>`, `<datalist>`,
  `input list` e `input` de tipo date/time/number/range/color, y los roles
  combobox, listbox, option, menu, menuitem, tablist, tabpanel, spinbutton,
  progressbar y grid.
- Build: `web/package.json` declara `sideEffects: false`, de modo que el login ya
  no descarga los componentes que solo usan otras pantallas (antes viajaban en el
  paquete inicial todos los del sistema). Paquete inicial: 446 kB (antes 515 kB
  con los componentes nuevos, sobre el presupuesto de 500 kB).
- Catálogo: formulario de ejemplo migrado (titular con búsqueda, finalidad,
  fuentes múltiples, fecha, monto, plazo, aviso), calendario y nueva sección
  Navegación; paginación bajo la tabla y progreso en Estados.
- Corrección: el índice del catálogo y "Saltar al contenido" usaban `href="#…"`,
  que con `<base href="/">` llevaba al acceso. Ahora usan `routerLink` + `fragment`
  con desplazamiento a anclas del router.

Verificación de esta entrega: 30 pruebas Angular (14 nuevas de comportamiento y
teclado) y 8 pruebas de reglas/contraste aprobadas; build de producción sin avisos.
Revisión en Chrome a 1512 px, claro y oscuro: lista simple y múltiple, búsqueda,
calendario con teclado, campo numérico, menú, pestañas, paginación y progreso.
Pendiente: revisión visual a 375 px, lector de pantalla, Safari y Firefox.

## 2.0.0 — 2026-09-28

Rediseño visual completo: colores sólidos y precisos tomados del logo, estilo
sereno inspirado en Apple. Sin degradados, neones, desenfoques ni tintes acuarela.

- Paleta de marca exacta del logo (`--ds-brand-*`: Pino, Bosque, Salvia, Miel,
  Arena, Marfil, Cacao) y roles semánticos rederivados para claro y oscuro. El
  primario oscuro deja de ser menta pastel; se usa un verde de marca aclarado.
- **Ruptura:** se eliminan `--ds-glass`, `--ds-blur` y los fondos teñidos
  `--ds-success-bg`, `--ds-warning-bg`, `--ds-danger-bg`, `--ds-info-bg`. Las
  superficies son neutras y opacas; el color es una señal (punto del badge, icono
  de la alerta, acción principal).
- Nuevos tokens: `--ds-link`, `--ds-surface-strong`, `--ds-surface-raised`,
  `--ds-inverse*`, `--ds-wordmark*`, `--ds-font-display`, `--ds-display-fit`,
  `--ds-radius-xs/xl`, `--ds-shadow-sm`, `--ds-dialog-width`, entre otros.
- `ds-brand-surface`: superficie sólida verde pino que reasigna roles con AA.
- Componentes: botón rectangular redondeado (44 px) con variante `icon`; danger
  como texto rojo sobre neutro; badge con punto sólido; alerta con icono por tono;
  error de campo con icono; checkbox y radio dibujados por el sistema; select con
  chevron propio; diálogo y toast opacos (toast en superficie inversa).
- Nuevos: `ds-logo` (emblema como icono de aplicación), `ds-swatch`, utilidades
  `ds-actions` y `ds-lead`; iconos info/success/warning/danger/tray/building/flask.
- Marca: `public/doggycredit-mark.svg` (emblema sin texto ni fondo) para el
  wordmark, el favicon y `ds-logo`.
- Correcciones: `ds-divider` medía 0 px dentro de columnas flex; un `select` se
  estiraba junto a campos con ayuda; la sombra de "Saltar al contenido" asomaba.
- Login rediseñado: panel de marca en verde pino y acceso limpio sobre marfil.
  Catálogo reorganizado en 9 secciones (Color y Tipografía y forma nuevas).
- Checker: rechaza `gradient()`, `filter`, `backdrop-filter` y `mix-blend-mode`
  en pantallas. `ds:test` verifica contraste en claro, oscuro y superficie de marca.

Verificación de esta entrega: 15 pruebas Angular y 7 pruebas de reglas/contraste
aprobadas; build de producción aprobado sin avisos de presupuesto. Revisión visual
en Chrome headless a 375, 768, 1024 y 1440 px en claro y oscuro.

## 1.0.0 — 2026-09-28

- Tokens semánticos, tipografía nativa, temas claro/oscuro/sistema y preferencias de accesibilidad.
- Componentes standalone, controles nativos, formularios, estados, tablas, diálogo y notificaciones.
- Catálogo interactivo en /design-system; login migrado a la misma API.
- Verificación de uso obligatoria en scripts de desarrollo, build y CI.
- Pruebas de interacción, de contraste de tokens y de rechazo de divergencias.

Verificación de esta entrega: 12 pruebas Angular y 5 pruebas de reglas/contraste
aprobadas; build de producción aprobado (402.56 kB iniciales, catálogo en carga
diferida). Comprobación de contraste sobre los pares semánticos de ambos temas.
La revisión visual y el recorrido modal en navegador real están pendientes por
restricción del servidor local en el entorno de ejecución.
