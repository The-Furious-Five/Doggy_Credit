import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DsReveal } from './index';

@Component({
  imports: [DsReveal],
  template: `<p dsReveal="left" [dsRevealOrder]="2">Hola</p>`,
})
class Host {}

describe('Aparición al hacer scroll', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('aparece cuando el elemento entra en pantalla, con dirección y orden', () => {
    let notify: (entries: Partial<IntersectionObserverEntry>[]) => void = () => undefined;
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(callback: typeof notify) {
          notify = callback;
        }
        observe() {}
        disconnect() {}
      },
    );
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const p = fixture.nativeElement.querySelector('p') as HTMLElement;
    expect(p.classList).toContain('ds-reveal');
    expect(p.dataset['reveal']).toBe('left');
    expect(p.classList).not.toContain('is-visible');

    notify([{ isIntersecting: true }]);
    fixture.detectChanges();
    expect(p.classList).toContain('is-visible');
  });

  it('sin IntersectionObserver se muestra de inmediato', () => {
    vi.stubGlobal('IntersectionObserver', undefined);
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').classList).toContain('is-visible');
  });
});
