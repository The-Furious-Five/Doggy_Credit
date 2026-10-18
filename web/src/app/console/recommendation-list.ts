import { Component, input } from '@angular/core';
import { Recomendacion, RecomendacionTipoProductoEnum } from '../api';
import { money } from './client-format';

const PRODUCT_NAME: Record<RecomendacionTipoProductoEnum, string> = {
  [RecomendacionTipoProductoEnum.Microcredito]: 'Microcrédito',
  [RecomendacionTipoProductoEnum.Verde]: 'Crédito ecológico',
  [RecomendacionTipoProductoEnum.Pyme]: 'Crédito PYME',
};

/**
 * C-06: productos recomendados de una evaluación, del más adecuado al menos.
 * `items` sin definir = no disponibles (plan sin recomendaciones o resultado parcial);
 * lista vacía = se evaluaron y ningún producto aplica.
 */
@Component({
  selector: 'dc-recommendation-list',
  template: `
    @if (items() === undefined) {
      <p class="ds-caption">
        Las recomendaciones de productos están disponibles en el plan Pro y no se emiten con resultados parciales.
      </p>
    } @else if (items()!.length === 0) {
      <p class="ds-muted">Ningún producto del catálogo aplica para esta evaluación.</p>
    } @else {
      <ol class="list" aria-label="Productos recomendados">
        @for (item of items()!; track item.tipoProducto) {
          <li>
            <span class="ds-stack-sm">
              <strong>{{ name(item) }}</strong>
              <span class="ds-caption">{{ item.razon }}</span>
            </span>
            <strong class="num">{{ amount(item) }}</strong>
          </li>
        }
      </ol>
    }
  `,
})
export class RecommendationList {
  readonly items = input<Recomendacion[] | undefined>(undefined);

  name(item: Recomendacion): string {
    return PRODUCT_NAME[item.tipoProducto];
  }

  amount(item: Recomendacion): string {
    return money({ amount: item.montoSugerido, currency: item.moneda });
  }
}
