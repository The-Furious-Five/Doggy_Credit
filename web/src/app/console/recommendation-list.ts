import { Component, input } from '@angular/core';
import { Recomendacion, RecomendacionTipoProductoEnum } from '../api';
import { DsAlert } from '../design-system';
import { money } from './client-format';

const PRODUCT_NAME: Record<RecomendacionTipoProductoEnum, string> = {
  [RecomendacionTipoProductoEnum.Microcredito]: 'Microcrédito',
  [RecomendacionTipoProductoEnum.Verde]: 'Crédito ecológico',
  [RecomendacionTipoProductoEnum.Pyme]: 'Crédito PYME',
};

/**
 * C-06: productos recomendados de una evaluación, del más adecuado al menos.
 * `items` sin definir = no disponibles: con `partial` porque no se recomienda con fuentes
 * fallidas; si no, porque el plan no incluye el ranking (C-16: invitación al plan Pro, no un
 * error). Lista vacía = se evaluaron y ningún producto aplica.
 */
@Component({
  selector: 'dc-recommendation-list',
  imports: [DsAlert],
  styleUrl: './page.scss',
  template: `
    @if (items() === undefined) {
      @if (partial()) {
        <p class="ds-caption">No se recomiendan productos con un resultado parcial: faltaron fuentes al evaluar.</p>
      } @else {
        <ds-alert tone="info" heading="Ranking disponible en el plan Pro">
          Con el plan Pro verás los productos ordenados por ajuste, con monto y plazo sugeridos y sus razones.
        </ds-alert>
      }
    } @else if (items()!.length === 0) {
      <p class="ds-muted">Ningún producto del catálogo aplica para esta evaluación.</p>
    } @else {
      <ol class="list" aria-label="Productos recomendados">
        @for (item of items()!; track item.tipoProducto) {
          <li>
            <span class="ds-stack-sm">
              <strong>{{ name(item) }}</strong>
              <span class="ds-caption">{{ item.razon }}</span>
              @if (item.razones?.length) {
                <!-- Sin lista anidada: el E2E cuenta un listitem por producto recomendado. -->
                <span class="reasons" [attr.data-reasons]="name(item)">
                  @for (reason of item.razones; track reason) {
                    <span class="ds-caption">{{ reason }}</span>
                  }
                </span>
              }
            </span>
            <span class="ds-stack-sm">
              <strong class="num">{{ amount(item) }}</strong>
              @if (item.plazoSugeridoMeses) {
                <span class="ds-caption">a {{ item.plazoSugeridoMeses }} meses</span>
              }
            </span>
          </li>
        }
      </ol>
    }
  `,
})
export class RecommendationList {
  readonly items = input<Recomendacion[] | undefined>(undefined);
  /** true si la evaluación salió PARCIAL: el ranking falta por las fuentes, no por el plan. */
  readonly partial = input(false);

  name(item: Recomendacion): string {
    return PRODUCT_NAME[item.tipoProducto];
  }

  amount(item: Recomendacion): string {
    return money({ amount: item.montoSugerido, currency: item.moneda });
  }
}
