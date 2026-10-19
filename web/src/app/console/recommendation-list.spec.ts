import { TestBed } from '@angular/core/testing';
import { Recomendacion, RecomendacionMonedaEnum, RecomendacionTipoProductoEnum } from '../api';
import { RecommendationList } from './recommendation-list';

const item = (tipo: RecomendacionTipoProductoEnum, monto: string, razon: string): Recomendacion => ({
  tipoProducto: tipo,
  moneda: RecomendacionMonedaEnum.Bob,
  montoSugerido: monto,
  razon,
});

describe('Lista de recomendaciones (C-06)', () => {
  function render(items: Recomendacion[] | undefined) {
    const fixture = TestBed.createComponent(RecommendationList);
    fixture.componentRef.setInput('items', items);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('sin recomendaciones disponibles explica que son del plan Pro y no de parciales', () => {
    expect(render(undefined).textContent).toContain('disponibles en el plan Pro');
  });

  it('si se evaluaron y ninguna aplica, lo dice', () => {
    expect(render([]).textContent).toContain('Ningún producto del catálogo aplica');
  });

  it('muestra los productos en orden con su monto y su razón', () => {
    const page = render([
      item(RecomendacionTipoProductoEnum.Verde, '3000.00', 'Destino verde'),
      item(RecomendacionTipoProductoEnum.Microcredito, '2500.00', 'Banda B cumple la mínima C'),
    ]);
    const rows = [...page.querySelectorAll('li')].map((li) => li.textContent ?? '');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toContain('Crédito ecológico');
    expect(rows[0]).toContain('Bs 3.000');
    expect(rows[0]).toContain('Destino verde');
    expect(rows[1]).toContain('Microcrédito');
  });
});
