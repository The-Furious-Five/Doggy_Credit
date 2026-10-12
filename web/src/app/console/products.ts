import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { Producto, ProductoTipoEnum, ProductosService } from '../api';
import {
  DsAlert,
  DsButton,
  DsCard,
  DsEmptyState,
  DsIcon,
  DsIconName,
  DsLink,
  DsSkeleton,
  DsSwitch,
  DsToastService,
} from '../design-system';
import { productNames } from './product-format';
import { money } from './client-format';

/** Presentación de cada tipo de producto. Los datos (rangos, estado) vienen de la API. */
const PRESENTATION: Record<ProductoTipoEnum, { name: string; icon: DsIconName; summary: string }> = {
  [ProductoTipoEnum.Microcredito]: {
    name: productNames[ProductoTipoEnum.Microcredito],
    icon: 'coin',
    summary: 'Montos pequeños y aprobación ágil para personas y emprendedores.',
  },
  [ProductoTipoEnum.Verde]: {
    name: productNames[ProductoTipoEnum.Verde],
    icon: 'leaf',
    summary: 'Financia paneles solares, eficiencia energética y movilidad limpia.',
  },
  [ProductoTipoEnum.Pyme]: {
    name: productNames[ProductoTipoEnum.Pyme],
    icon: 'store',
    summary: 'Capital de trabajo e inversión para pequeñas y medianas empresas.',
  },
};

type State = 'loading' | 'ready' | 'forbidden' | 'error';

@Component({
  selector: 'dc-products',
  imports: [RouterLink, DsAlert, DsButton, DsCard, DsEmptyState, DsIcon, DsLink, DsSkeleton, DsSwitch],
  styleUrl: './page.scss',
  template: `
    <header class="page-header">
      <div class="ds-stack-sm">
        <h1>Productos</h1>
        <p class="ds-muted">Líneas de crédito que DoggyCredit puede recomendar a tus clientes.</p>
      </div>
    </header>

    @switch (state()) {
      @case ('loading') {
        <div class="ds-grid" aria-busy="true" aria-label="Cargando productos">
          @for (slot of [1, 2, 3]; track slot) {
            <ds-card><ds-skeleton /></ds-card>
          }
        </div>
      }
      @case ('forbidden') {
        <ds-alert tone="warning" heading="Solo el administrador del tenant puede ver los productos">
          Pide a quien administra tu institución que revise el catálogo.
        </ds-alert>
      }
      @case ('error') {
        <ds-alert tone="danger" heading="No pudimos cargar los productos">
          <p>Revisa tu conexión y vuelve a intentarlo.</p>
          <button dsButton variant="secondary" (click)="load()">Reintentar</button>
        </ds-alert>
      }
      @case ('ready') {
        @if (products().length === 0) {
          <ds-empty-state
            heading="Sin productos"
            description="Tu institución todavía no tiene productos en el catálogo."
            icon="tray"
          />
        } @else {
          <div class="ds-grid">
            @for (product of products(); track product.id) {
              <ds-card>
                <div class="ds-stack">
                  <div class="ds-row ds-between">
                    <span class="icon-tile"><ds-icon [name]="presentation(product).icon" /></span>
                    <ds-switch
                      [checked]="product.activo"
                      [disabled]="saving() === product.id"
                      (checkedChange)="setActive(product, $event)"
                      >Activo<span class="ds-sr-only">: {{ presentation(product).name }}</span></ds-switch
                    >
                  </div>
                  <div class="ds-stack-sm">
                    <h2>{{ presentation(product).name }}</h2>
                    <p class="ds-muted">{{ presentation(product).summary }}</p>
                  </div>
                  <dl class="facts">
                    <div><dt class="ds-caption">Montos</dt><dd>{{ range(product) }}</dd></div>
                    <div><dt class="ds-caption">Plazos</dt><dd>{{ term(product) }}</dd></div>
                  </dl>
                  <a dsLink routerLink="/app/politicas">Ver políticas <ds-icon name="arrow" /></a>
                </div>
              </ds-card>
            }
          </div>
        }
      }
    }
  `,
})
export class Products {
  private readonly api = inject(ProductosService);
  private readonly toast = inject(DsToastService);

  readonly state = signal<State>('loading');
  readonly products = signal<Producto[]>([]);
  /** Id del producto que se está guardando: su interruptor queda deshabilitado mientras tanto. */
  readonly saving = signal<string | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.state.set('loading');
    this.api.listarProductos().subscribe({
      next: (products) => {
        this.products.set(products);
        this.state.set('ready');
      },
      error: (error: HttpErrorResponse) => this.state.set(error.status === 403 ? 'forbidden' : 'error'),
    });
  }

  setActive(product: Producto, activo: boolean): void {
    this.saving.set(product.id);
    // Actualización optimista: el valor cambia ya, y si el servidor falla vuelve atrás.
    // Así la entrada [checked] siempre cambia y el interruptor muestra el estado real.
    this.replace({ ...product, activo });
    this.api
      .actualizarProducto(product.id, {
        activo,
        montoMinimo: product.montoMinimo,
        montoMaximo: product.montoMaximo,
        plazoMinimoMeses: product.plazoMinimoMeses,
        plazoMaximoMeses: product.plazoMaximoMeses,
      })
      .subscribe({
        next: (updated) => {
          this.replace(updated);
          this.saving.set(null);
          this.toast.show(`${this.presentation(updated).name} ${updated.activo ? 'activado' : 'desactivado'}.`);
        },
        error: () => {
          this.replace(product);
          this.saving.set(null);
          this.toast.show('No pudimos guardar el cambio. Vuelve a intentarlo.');
        },
      });
  }

  private replace(product: Producto): void {
    this.products.update((all) => all.map((p) => (p.id === product.id ? product : p)));
  }

  presentation(product: Producto) {
    return PRESENTATION[product.tipo];
  }

  range(product: Producto): string {
    const currency = product.moneda;
    return `${money({ amount: product.montoMinimo, currency })} – ${money({ amount: product.montoMaximo, currency })}`;
  }

  term(product: Producto): string {
    return `${product.plazoMinimoMeses} a ${product.plazoMaximoMeses} meses`;
  }
}
