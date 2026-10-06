import { Directive, ElementRef, OnDestroy, OnInit, inject, input, signal } from '@angular/core';

export type DsRevealDirection = 'up' | 'left' | 'right' | 'scale';

/**
 * Aparición al hacer scroll: el elemento entra (sube, se desliza o crece) la
 * primera vez que llega a la pantalla. `dsRevealOrder` escalona elementos de
 * una misma fila. Sin IntersectionObserver queda visible de inmediato; con
 * movimiento reducido aparece sin desplazarse.
 */
@Directive({
  selector: '[dsReveal]',
  host: {
    class: 'ds-reveal',
    '[attr.data-reveal]': 'dsReveal() || "up"',
    '[class.is-visible]': 'visible()',
    '[style.--ds-reveal-order]': 'dsRevealOrder()',
  },
})
export class DsReveal implements OnInit, OnDestroy {
  readonly dsReveal = input<DsRevealDirection | ''>('');
  readonly dsRevealOrder = input(0);
  readonly visible = signal(false);
  private readonly element: ElementRef<HTMLElement> = inject(ElementRef);
  private observer?: IntersectionObserver;

  ngOnInit(): void {
    if (typeof IntersectionObserver !== 'function') {
      this.visible.set(true);
      return;
    }
    this.observer = new IntersectionObserver(
      (entries: IntersectionObserverEntry[]) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          this.visible.set(true);
          this.observer?.disconnect();
        }
      },
      { threshold: 0.15, rootMargin: '0px 0px -8% 0px' },
    );
    this.observer.observe(this.element.nativeElement);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
