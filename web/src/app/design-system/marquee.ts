import { Component, computed, input } from '@angular/core';

export interface DsMarqueeItem {
  /** Nombre visible; si hay `image`, es su texto alternativo. */
  label: string;
  /** Logo o imagen (ruta pública). Sin imagen se muestra el nombre. */
  image?: string;
}

/** Elementos por vuelta como mínimo: con pocos logos se repiten para llenar la franja. */
const MIN_PER_LOOP = 12;

/**
 * Franja que se desplaza sola, en bucle, de izquierda a derecha. Logos sueltos,
 * sin contenedores. Se pausa con el mouse o el foco; con movimiento reducido
 * queda quieta y en varias líneas. La copia que completa el bucle se oculta a
 * los lectores de pantalla.
 */
@Component({
  selector: 'ds-marquee',
  host: {
    class: 'ds-marquee',
    '[attr.data-direction]': 'direction()',
    '[style.--ds-marquee-duration]': 'duration()',
    role: 'region',
    '[attr.aria-label]': 'label()',
    tabindex: '0',
  },
  template: `
    <div class="ds-marquee__track">
      @for (copy of [0, 1]; track copy) {
        <ul class="ds-marquee__group" [attr.aria-hidden]="copy === 1 ? 'true' : null">
          @for (item of loop(); track $index) {
            <li class="ds-marquee__item" [attr.aria-hidden]="$index >= items().length ? 'true' : null">
              @if (item.image) {
                <img class="ds-marquee__logo" [src]="item.image" [alt]="item.label" loading="lazy" decoding="async" />
              } @else {
                <span class="ds-marquee__label">{{ item.label }}</span>
              }
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class DsMarquee {
  readonly items = input.required<DsMarqueeItem[]>();
  readonly label = input.required<string>();
  readonly direction = input<'right' | 'left'>('right');

  readonly loop = computed(() => {
    const items = this.items();
    if (!items.length) return [];
    const times = Math.max(1, Math.ceil(MIN_PER_LOOP / items.length));
    return Array.from({ length: times }, () => items).flat();
  });
  /** Velocidad constante: segundos proporcionales a los elementos por vuelta. */
  readonly duration = computed(() => `${Math.max(24, this.loop().length * 3.5)}s`);
}
