# Changelog

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
