import { DOCUMENT } from '@angular/common';
import { DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { DsField } from './primitives';

// Utilidades compartidas por los controles propios. No se exportan desde index.ts.

let sequence = 0;
export const nextId = (prefix: string): string => `${prefix}-${++sequence}`;

/** Texto comparable sin mayúsculas ni tildes, para búsquedas y escritura anticipada. */
export const normalize = (text: string): string =>
  text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

/** Vincula un control propio con su ds-field: id, etiqueta, ayuda, error y obligatoriedad. */
export function fieldContext(ownId: () => string, prefix: string) {
  const field = inject(DsField, { optional: true });
  const fallback = nextId(prefix);
  const controlId = computed(() => field?.controlId() || ownId() || fallback);
  return {
    controlId,
    labelId: computed(() => (field ? `${field.controlId()}-label` : null)),
    describedBy: computed(
      () =>
        [field?.hint() ? `${controlId()}-hint` : '', field?.error() ? `${controlId()}-error` : '']
          .filter(Boolean)
          .join(' ') || null,
    ),
    invalid: computed(() => Boolean(field?.error())),
    required: computed(() => Boolean(field?.required())),
  };
}

/** Escritura anticipada: acumula teclas durante medio segundo. */
export class Typeahead {
  private buffer = '';
  private timer?: ReturnType<typeof setTimeout>;
  get active(): boolean {
    return this.buffer.length > 0;
  }
  push(char: string): string {
    clearTimeout(this.timer);
    this.buffer += char;
    this.timer = setTimeout(() => this.reset(), 500);
    return this.buffer;
  }
  reset(): void {
    clearTimeout(this.timer);
    this.buffer = '';
  }
}

/** Primera etiqueta que empieza por `query`, buscando en círculo desde `start`. */
export function matchLabel(
  labels: readonly string[],
  query: string,
  start: number,
  skip: (index: number) => boolean = () => false,
): number {
  const wanted = normalize(query);
  for (let offset = 0; offset < labels.length; offset++) {
    const index = (Math.max(0, start) + offset) % labels.length;
    if (!skip(index) && normalize(labels[index]).startsWith(wanted)) return index;
  }
  return -1;
}

/** Siguiente índice habilitado desde `from` (incluido) en la dirección indicada, sin dar la vuelta. */
export function enabledIndex(
  items: readonly { disabled?: boolean }[],
  from: number,
  step: 1 | -1,
): number {
  for (let index = from; index >= 0 && index < items.length; index += step)
    if (!items[index].disabled) return index;
  return -1;
}

export interface DsPlacement {
  top: number;
  left: number;
  minWidth: number;
  maxHeight: number;
  above: boolean;
}

const GAP = 4;
const MARGIN = 8;

/** Coloca un panel bajo su ancla; si no cabe y arriba hay más espacio, lo coloca encima. */
export function placePanel(
  anchor: Pick<DOMRect, 'top' | 'bottom' | 'left' | 'width'>,
  viewport: { width: number; height: number },
  panel: { width: number; height: number },
): DsPlacement {
  const spaceBelow = viewport.height - anchor.bottom - GAP - MARGIN;
  const spaceAbove = anchor.top - GAP - MARGIN;
  const above = panel.height > spaceBelow && spaceAbove > spaceBelow;
  const maxHeight = Math.max(0, above ? spaceAbove : spaceBelow);
  const top = above ? anchor.top - GAP - Math.min(panel.height, maxHeight) : anchor.bottom + GAP;
  const width = Math.max(panel.width, anchor.width);
  const left = Math.max(MARGIN, Math.min(anchor.left, viewport.width - width - MARGIN));
  return { top, left, minWidth: anchor.width, maxHeight, above };
}

/**
 * Panel flotante anclado a un control. Usa posición fija para no quedar recortado por
 * contenedores con desplazamiento (diálogos, tablas) y se cierra al pulsar fuera del host.
 */
export function floatingPanel(
  anchor: () => HTMLElement | undefined,
  panel: () => HTMLElement | undefined,
) {
  const document = inject(DOCUMENT);
  const host: HTMLElement = inject(ElementRef).nativeElement;
  const view = document.defaultView;
  const open = signal(false);
  const placement = signal<DsPlacement | null>(null);

  const place = (): void => {
    const target = anchor();
    if (!open() || !target || !view) return;
    const content = panel();
    placement.set(
      placePanel(
        target.getBoundingClientRect(),
        { width: view.innerWidth, height: view.innerHeight },
        { width: content?.offsetWidth ?? 0, height: content?.scrollHeight ?? 0 },
      ),
    );
  };
  const dismiss = (event: Event): void => {
    if (!host.contains(event.target as Node)) hide();
  };
  const listen = (active: boolean): void => {
    if (active) {
      view?.addEventListener('resize', place);
      document.addEventListener('scroll', place, true);
      document.addEventListener('pointerdown', dismiss, true);
    } else {
      view?.removeEventListener('resize', place);
      document.removeEventListener('scroll', place, true);
      document.removeEventListener('pointerdown', dismiss, true);
    }
  };
  function show(): void {
    if (open()) return;
    open.set(true);
    listen(true);
  }
  function hide(): void {
    if (!open()) return;
    open.set(false);
    placement.set(null);
    listen(false);
  }
  inject(DestroyRef).onDestroy(hide);
  return { open, placement, place, show, hide };
}
