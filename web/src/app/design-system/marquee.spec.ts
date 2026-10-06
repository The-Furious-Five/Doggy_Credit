import { TestBed } from '@angular/core/testing';
import { DsMarquee, DsMarqueeItem } from './index';

describe('Marquesina', () => {
  function render(items: DsMarqueeItem[]) {
    const fixture = TestBed.createComponent(DsMarquee);
    fixture.componentRef.setInput('label', 'Bancos');
    fixture.componentRef.setInput('items', items);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra logos sueltos con su nombre como texto alternativo', () => {
    const host = render([{ label: 'Banco Unión', image: 'integraciones/union.png' }]);
    const logo = host.querySelector<HTMLImageElement>('.ds-marquee__group:first-child img')!;
    expect(logo.getAttribute('src')).toBe('integraciones/union.png');
    expect(logo.alt).toBe('Banco Unión');
  });

  it('sin imagen muestra el nombre', () => {
    const host = render([{ label: 'Infocred' }]);
    expect(host.querySelector('.ds-marquee__label')?.textContent).toContain('Infocred');
  });

  it('repite pocos elementos para llenar la franja, sin duplicarlos para lectores de pantalla', () => {
    const host = render([{ label: 'A' }, { label: 'B' }]);
    const groups = host.querySelectorAll('.ds-marquee__group');
    expect(groups).toHaveLength(2);
    expect(groups[1].getAttribute('aria-hidden')).toBe('true');
    const items = [...groups[0].querySelectorAll('li')];
    expect(items.length).toBeGreaterThanOrEqual(12);
    expect(items.filter((li) => li.getAttribute('aria-hidden') !== 'true')).toHaveLength(2);
  });

  it('es una región con nombre, enfocable para pausarla con el teclado', () => {
    const host = render([{ label: 'A' }]);
    expect(host.getAttribute('role')).toBe('region');
    expect(host.getAttribute('aria-label')).toBe('Bancos');
    expect(host.getAttribute('tabindex')).toBe('0');
  });
});
