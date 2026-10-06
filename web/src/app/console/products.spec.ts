import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Producto, ProductoBandaMinimaEnum, ProductoMonedaEnum, ProductoTipoEnum, ProductosService } from '../api';
import { Products } from './products';

const micro: Producto = {
  id: '11111111-1111-4111-8111-111111111111',
  tipo: ProductoTipoEnum.Microcredito,
  moneda: ProductoMonedaEnum.Bob,
  montoMinimo: '1000.00',
  montoMaximo: '20000.00',
  plazoMinimoMeses: 3,
  plazoMaximoMeses: 24,
  tasaAnual: '11.50',
  bandaMinima: ProductoBandaMinimaEnum.C,
  activo: true,
};
const pyme: Producto = {
  ...micro,
  id: '22222222-2222-4222-8222-222222222222',
  tipo: ProductoTipoEnum.Pyme,
  montoMinimo: '50000.00',
  montoMaximo: '700000.00',
  plazoMinimoMeses: 6,
  plazoMaximoMeses: 84,
  bandaMinima: ProductoBandaMinimaEnum.B,
  activo: false,
};

describe('Productos conectados a la API', () => {
  const api = { listarProductos: vi.fn(), actualizarProducto: vi.fn() };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [Products],
      providers: [provideRouter([]), { provide: ProductosService, useValue: api }],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(Products);
    fixture.detectChanges();
    return { fixture, page: fixture.nativeElement as HTMLElement };
  }

  it('muestra el catálogo que devuelve la API, con sus rangos', () => {
    api.listarProductos.mockReturnValue(of([micro, pyme]));
    const { page } = render();

    expect(page.textContent).toContain('Microcrédito');
    expect(page.textContent).toContain('Crédito PYME');
    expect(page.textContent).toContain('3 a 24 meses');
    expect(page.textContent).toContain('Bs 1.000 – Bs 20.000');
    expect(page.querySelectorAll('ds-card')).toHaveLength(2);
  });

  it('muestra los montos en la moneda del producto', () => {
    api.listarProductos.mockReturnValue(of([{ ...micro, moneda: ProductoMonedaEnum.Usd }]));
    const { page } = render();

    expect(page.textContent).toContain('US$ 1.000 – US$ 20.000');
    expect(page.textContent).not.toContain('Bs');
  });

  it('muestra un estado vacío si el tenant no tiene productos', () => {
    api.listarProductos.mockReturnValue(of([]));
    const { page } = render();

    expect(page.textContent).toContain('Sin productos');
    expect(page.querySelectorAll('ds-card')).toHaveLength(0);
  });

  it('muestra un error con reintento si la API falla', () => {
    api.listarProductos.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { fixture, page } = render();

    expect(page.textContent).toContain('No pudimos cargar los productos');
    api.listarProductos.mockReturnValue(of([micro]));
    (page.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(api.listarProductos).toHaveBeenCalledTimes(2);
    expect(page.textContent).toContain('Microcrédito');
  });

  it('explica que solo el administrador puede ver los productos (403)', () => {
    api.listarProductos.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const { page } = render();

    expect(page.textContent).toContain('Solo el administrador del tenant puede ver los productos');
  });

  it('el interruptor guarda el cambio sin tocar los rangos', () => {
    api.listarProductos.mockReturnValue(of([micro]));
    api.actualizarProducto.mockReturnValue(of({ ...micro, activo: false }));
    const { fixture } = render();

    fixture.componentInstance.setActive(micro, false);

    expect(api.actualizarProducto).toHaveBeenCalledWith(micro.id, {
      activo: false,
      montoMinimo: '1000.00',
      montoMaximo: '20000.00',
      plazoMinimoMeses: 3,
      plazoMaximoMeses: 24,
    });
    expect(fixture.componentInstance.products()[0].activo).toBe(false);
  });

  it('si el guardado falla, el producto vuelve a su estado anterior', () => {
    api.listarProductos.mockReturnValue(of([micro]));
    api.actualizarProducto.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { fixture } = render();

    fixture.componentInstance.setActive(micro, false);

    expect(fixture.componentInstance.products()[0].activo).toBe(true);
    expect(fixture.componentInstance.saving()).toBeNull();
  });
});
