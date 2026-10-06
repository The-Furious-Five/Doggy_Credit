import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DsSlide, DsSlides } from './index';

@Component({
  imports: [DsSlides, DsSlide],
  template: `
    <ds-slides label="Productos" [nav]="nav">
      <ds-slide label="Uno"><p>Primera</p></ds-slide>
      <ds-slide label="Dos"><p>Segunda</p></ds-slide>
      <ds-slide label="Tres"><p>Tercera</p></ds-slide>
    </ds-slides>
  `,
})
class Host {
  nav: 'tabs' | 'dots' = 'tabs';
}

describe('Diapositivas', () => {
  function render(nav: 'tabs' | 'dots' = 'tabs') {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.nav = nav;
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    const active = () => page.querySelector('ds-slide.is-active p')?.textContent;
    return { fixture, page, active };
  }

  it('muestra una sola diapositiva y las pestañas la cambian', () => {
    const { fixture, page, active } = render('tabs');
    expect(active()).toBe('Primera');
    expect(page.querySelectorAll('ds-slide.is-active')).toHaveLength(1);

    const tabs = page.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    expect([...tabs].map((t) => t.textContent?.trim())).toEqual(['Uno', 'Dos', 'Tres']);
    tabs[2].click();
    fixture.detectChanges();
    expect(active()).toBe('Tercera');
    expect(tabs[2].getAttribute('aria-selected')).toBe('true');
  });

  it('las diapositivas ocultas no se leen ni reciben foco', () => {
    const { page } = render();
    const hidden = page.querySelectorAll('ds-slide:not(.is-active)');
    hidden.forEach((slide) => {
      expect(slide.getAttribute('aria-hidden')).toBe('true');
      expect(slide.hasAttribute('inert')).toBe(true);
    });
    const tab = page.querySelector('[role="tab"]')!;
    const panel = page.querySelector('ds-slide')!;
    expect(tab.getAttribute('aria-controls')).toBe(panel.id);
    expect(panel.getAttribute('aria-labelledby')).toBe(tab.id);
  });

  it('con puntos y flechas avanza, retrocede y da la vuelta', () => {
    const { fixture, page, active } = render('dots');
    const next = page.querySelector<HTMLButtonElement>('[aria-label="Siguiente"]')!;
    const prev = page.querySelector<HTMLButtonElement>('[aria-label="Anterior"]')!;
    next.click();
    fixture.detectChanges();
    expect(active()).toBe('Segunda');
    prev.click();
    prev.click();
    fixture.detectChanges();
    expect(active()).toBe('Tercera');
    expect(page.querySelectorAll('.ds-slides__dot')).toHaveLength(3);
  });

  it('se maneja con las flechas del teclado', () => {
    const { fixture, page, active } = render('tabs');
    const list = page.querySelector('[role="tablist"]')!;
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(active()).toBe('Segunda');
    list.dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(active()).toBe('Tercera');
  });
});
