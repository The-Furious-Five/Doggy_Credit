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
  function render(items: Recomendacion[] | undefined, partial = false) {
    const fixture = TestBed.createComponent(RecommendationList);
    fixture.componentRef.setInput('items', items);
    fixture.componentRef.setInput('partial', partial);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('C-16: sin ranking por el plan, invita al plan Pro sin mostrar un error', () => {
    const page = render(undefined);
    expect(page.querySelector('ds-alert')?.getAttribute('data-tone')).toBe('info');
    expect(page.textContent).toContain('Ranking disponible en el plan Pro');
    expect(page.textContent).not.toContain('resultado parcial');
  });

  it('C-16: sin ranking por un resultado parcial, lo explica y no invita al plan Pro', () => {
    const page = render(undefined, true);
    expect(page.textContent).toContain('resultado parcial');
    expect(page.querySelector('ds-alert')).toBeNull();
  });

  it('C-16: muestra el plazo sugerido y las razones del ajuste (E-16)', () => {
    const page = render([
      {
        ...item(RecomendacionTipoProductoEnum.Microcredito, '3000.00', 'Elegible: cumple banda, monto y plazo'),
        plazoSugeridoMeses: 12,
        razones: ['Banda B cumple la mínima C', 'Cuota BOB 280 no supera la capacidad mensual BOB 900'],
      },
    ]);
    expect(page.textContent).toContain('a 12 meses');
    const reasons = [...page.querySelectorAll('[data-reasons="Microcrédito"] > span')].map((s) => s.textContent);
    expect(reasons).toEqual(['Banda B cumple la mínima C', 'Cuota BOB 280 no supera la capacidad mensual BOB 900']);
  });

  it('C-16: con el monto enmascarado por el servidor no muestra la cifra', () => {
    const page = render([item(RecomendacionTipoProductoEnum.Pyme, '••••••', 'Elegible')]);
    expect(page.textContent).toContain('••••••');
    expect(page.textContent).not.toMatch(/Bs \d/);
  });

  it('una recomendación anterior a E-16 (sin plazo ni razones) se sigue mostrando', () => {
    const page = render([item(RecomendacionTipoProductoEnum.Pyme, '5000.00', 'Banda A')]);
    expect(page.textContent).not.toContain('meses');
    expect(page.querySelector('[data-reasons]')).toBeNull();
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
