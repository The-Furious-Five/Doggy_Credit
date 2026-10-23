import { Component, computed, input } from '@angular/core';
import {
  CriterioElegibilidad,
  CriterioElegibilidadCodigoEnum,
  ElegibilidadProducto,
  ElegibilidadProductoTipoProductoEnum,
} from '../api';
import { DsBadge } from '../design-system';

const PRODUCT_NAME: Record<ElegibilidadProductoTipoProductoEnum, string> = {
  [ElegibilidadProductoTipoProductoEnum.Microcredito]: 'Microcrédito',
  [ElegibilidadProductoTipoProductoEnum.Verde]: 'Crédito ecológico',
  [ElegibilidadProductoTipoProductoEnum.Pyme]: 'Crédito PYME',
};

const CRITERION_NAME: Record<CriterioElegibilidadCodigoEnum, string> = {
  [CriterioElegibilidadCodigoEnum.BandaMinima]: 'Banda mínima',
  [CriterioElegibilidadCodigoEnum.RangoMonto]: 'Monto',
  [CriterioElegibilidadCodigoEnum.RangoPlazo]: 'Plazo',
  [CriterioElegibilidadCodigoEnum.TipoTitular]: 'Tipo de titular',
  [CriterioElegibilidadCodigoEnum.Destino]: 'Destino',
  [CriterioElegibilidadCodigoEnum.CapacidadPago]: 'Capacidad de pago',
};

/**
 * C-16 (LOS-140), RF-REC-02: elegibilidad de cada producto activo al evaluar (E-16), en todos
 * los planes. `items` sin definir = evaluación anterior a E-16 (no se recalcula); lista vacía =
 * no había productos activos. Los montos del titular ya llegan enmascarados del servidor (A-10).
 * La región «Productos elegibles» y las marcas ✓ / ✗ son las que espera el E2E de A-16
 * (e2e/tests/eligibility-recommendations.spec.ts).
 */
@Component({
  selector: 'dc-product-eligibility',
  imports: [DsBadge],
  styleUrl: './page.scss',
  template: `
    <section class="ds-stack-sm" aria-labelledby="productos-elegibles">
      <h3 id="productos-elegibles">Productos elegibles</h3>
    @if (items() === undefined) {
      <p class="ds-caption">Esta evaluación es anterior al detalle de elegibilidad por producto.</p>
    } @else if (items()!.length === 0) {
      <p class="ds-muted">No había productos activos en el catálogo al evaluar.</p>
    } @else {
      @if (noneEligible()) {
        <p class="ds-muted">Ningún producto del catálogo aplica para esta evaluación.</p>
      }
      <ul class="list" aria-label="Elegibilidad por producto">
        @for (product of items()!; track product.productoId) {
          <li>
            <div class="ds-stack-sm">
              <span class="ds-row">
                <strong>{{ name(product) }}</strong>
                <ds-badge [tone]="product.elegible ? 'success' : 'neutral'">
                  {{ product.elegible ? 'Elegible' : 'No elegible' }}
                </ds-badge>
              </span>
              <ul class="criteria" [attr.aria-label]="'Criterios de ' + name(product)">
                @for (criterion of product.criterios; track criterion.codigo) {
                  <li [attr.data-met]="criterion.cumple">
                    <span class="mark" aria-hidden="true">{{ criterion.cumple ? '✓' : '✗' }}</span>
                    <span>
                      <span class="ds-sr-only">{{ criterion.cumple ? 'Cumple' : 'No cumple' }}:</span>
                      {{ criterionName(criterion) }} · {{ criterion.detalle }}
                    </span>
                  </li>
                }
              </ul>
            </div>
          </li>
        }
      </ul>
    }
    </section>
  `,
})
export class ProductEligibility {
  readonly items = input<ElegibilidadProducto[] | undefined>(undefined);

  readonly noneEligible = computed(() => (this.items() ?? []).every((product) => !product.elegible));

  name(product: ElegibilidadProducto): string {
    return PRODUCT_NAME[product.tipoProducto];
  }

  criterionName(criterion: CriterioElegibilidad): string {
    return CRITERION_NAME[criterion.codigo];
  }
}
