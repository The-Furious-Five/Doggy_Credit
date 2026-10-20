import { TestBed } from '@angular/core/testing';
import {
  CriterioElegibilidadCodigoEnum as Codigo,
  ElegibilidadProducto,
  ElegibilidadProductoMonedaEnum,
  ElegibilidadProductoTipoProductoEnum as Tipo,
} from '../api';
import { ProductEligibility } from './product-eligibility';

const criterios = (failing: Codigo[] = [], detalle: Partial<Record<Codigo, string>> = {}) =>
  Object.values(Codigo).map((codigo) => ({
    codigo,
    cumple: !failing.includes(codigo),
    detalle: detalle[codigo] ?? `detalle de ${codigo}`,
  }));

const product = (tipo: Tipo, elegible: boolean, failing: Codigo[] = [], detalle = {}): ElegibilidadProducto => ({
  productoId: `p-${tipo}`,
  tipoProducto: tipo,
  moneda: ElegibilidadProductoMonedaEnum.Bob,
  elegible,
  criterios: criterios(failing, detalle),
});

describe('Elegibilidad por producto (C-16)', () => {
  function render(items: ElegibilidadProducto[] | undefined) {
    const fixture = TestBed.createComponent(ProductEligibility);
    fixture.componentRef.setInput('items', items);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('en una evaluación anterior a E-16 lo dice, sin inventar criterios', () => {
    const page = render(undefined);
    expect(page.textContent).toContain('anterior al detalle de elegibilidad');
    expect(page.querySelector('ul')).toBeNull();
  });

  it('con la lista vacía explica que no había productos activos', () => {
    expect(render([]).textContent).toContain('No había productos activos');
  });

  it('muestra cada producto como elegible o no, con sus seis criterios y su detalle', () => {
    const page = render([
      product(Tipo.Microcredito, true),
      product(Tipo.Pyme, false, [Codigo.BandaMinima, Codigo.TipoTitular], {
        [Codigo.BandaMinima]: 'Banda C por debajo de la mínima B',
      }),
    ]);
    const products = [...page.querySelectorAll('ul.list > li')];
    expect(products).toHaveLength(2);
    expect(products[0].textContent).toContain('Microcrédito');
    expect(products[0].querySelector('ds-badge')?.textContent?.trim()).toBe('Elegible');
    expect(products[1].querySelector('ds-badge')?.textContent?.trim()).toBe('No elegible');

    const criteria = [...products[1].querySelectorAll('.criteria > li')];
    expect(criteria).toHaveLength(6);
    const banda = criteria.find((li) => li.textContent?.includes('Banda mínima'))!;
    expect(banda.getAttribute('data-met')).toBe('false');
    expect(banda.textContent).toContain('Banda C por debajo de la mínima B');
    // El ✓ / ✗ no depende solo del ícono: el lector de pantalla oye «Cumple» o «No cumple».
    expect(banda.querySelector('.ds-sr-only')?.textContent).toContain('No cumple');
    expect(criteria.filter((li) => li.getAttribute('data-met') === 'true')).toHaveLength(4);
    // Contrato del E2E de A-16: región «Productos elegibles» y marcas ✓ / ✗ visibles.
    const region = page.querySelector('section[aria-labelledby]')!;
    expect(page.querySelector(`#${region.getAttribute('aria-labelledby')}`)?.textContent).toBe('Productos elegibles');
    expect(banda.querySelector('.mark')?.textContent).toBe('✗');
    expect(criteria.find((li) => li.getAttribute('data-met') === 'true')?.querySelector('.mark')?.textContent).toBe('✓');
    expect(page.textContent).not.toContain('Ningún producto del catálogo aplica');
  });

  it('si ningún producto cumple, lo dice además de mostrar los criterios', () => {
    const page = render([product(Tipo.Verde, false, [Codigo.Destino])]);
    expect(page.textContent).toContain('Ningún producto del catálogo aplica');
    expect(page.querySelectorAll('.criteria > li')).toHaveLength(6);
  });

  it('muestra tal cual el monto que el servidor envía enmascarado', () => {
    const page = render([
      product(Tipo.Microcredito, true, [], { [Codigo.RangoMonto]: 'Monto Bs •••••• dentro del rango Bs 1.000 – 30.000' }),
    ]);
    expect(page.textContent).toContain('Monto Bs •••••• dentro del rango');
  });
});
