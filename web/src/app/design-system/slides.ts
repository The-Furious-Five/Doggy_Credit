import {
  Component,
  ElementRef,
  OnDestroy,
  booleanAttribute,
  computed,
  contentChildren,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { DsIcon } from './icon';

let slidesSequence = 0;

/** Una diapositiva de {@link DsSlides}. Su contenido es libre. */
@Component({
  selector: 'ds-slide',
  host: {
    class: 'ds-slide',
    role: 'tabpanel',
    '[id]': 'panelId',
    '[attr.aria-labelledby]': 'tabId',
    '[class.is-active]': 'active()',
    '[attr.aria-hidden]': 'active() ? null : "true"',
    '[attr.inert]': 'active() ? null : ""',
  },
  template: `<ng-content />`,
})
export class DsSlide {
  readonly label = input.required<string>();
  readonly active = signal(false);
  readonly element: ElementRef<HTMLElement> = inject(ElementRef);
  panelId = '';
  tabId = '';
}

/**
 * Diapositivas con selectores: al cambiar, la nueva entra desde la derecha y la
 * anterior sale por la izquierda (al revés si se retrocede). `nav="tabs"` muestra
 * pestañas con nombre; `nav="dots"`, puntos con flechas. `autoplay` (ms) avanza
 * solo y se pausa con el mouse o el foco; con movimiento reducido no hay
 * animación ni avance automático.
 */
@Component({
  selector: 'ds-slides',
  imports: [DsIcon],
  host: {
    class: 'ds-slides',
    '[attr.data-nav]': 'nav()',
    '(mouseenter)': 'paused.set(true)',
    '(mouseleave)': 'paused.set(false)',
    '(focusin)': 'paused.set(true)',
    '(focusout)': 'paused.set(false)',
  },
  template: `
    @if (nav() === 'tabs') {
      <div class="ds-slides__tabs" role="tablist" [attr.aria-label]="label()" (keydown)="onKey($event)">
        @for (slide of slides(); track slide; let i = $index) {
          <button
            type="button"
            role="tab"
            class="ds-slides__tab"
            [id]="slide.tabId"
            [attr.aria-controls]="slide.panelId"
            [attr.aria-selected]="i === index()"
            [attr.tabindex]="i === index() ? 0 : -1"
            (click)="go(i)"
          >
            {{ slide.label() }}
          </button>
        }
      </div>
    }
    <div class="ds-slides__viewport" [attr.aria-label]="label()" aria-roledescription="carrusel">
      <ng-content />
    </div>
    @if (nav() === 'dots') {
      <div class="ds-slides__controls">
        <button type="button" class="ds-slides__arrow" aria-label="Anterior" (click)="step(-1)">
          <ds-icon name="arrow" class="ds-slides__arrow-icon--back" />
        </button>
        <div class="ds-slides__dots" role="tablist" [attr.aria-label]="label()" (keydown)="onKey($event)">
          @for (slide of slides(); track slide; let i = $index) {
            <button
              type="button"
              role="tab"
              class="ds-slides__dot"
              [id]="slide.tabId"
              [attr.aria-controls]="slide.panelId"
              [attr.aria-selected]="i === index()"
              [attr.aria-label]="slide.label()"
              [attr.tabindex]="i === index() ? 0 : -1"
              (click)="go(i)"
            ></button>
          }
        </div>
        <button type="button" class="ds-slides__arrow" aria-label="Siguiente" (click)="step(1)">
          <ds-icon name="arrow" />
        </button>
      </div>
    }
  `,
})
export class DsSlides implements OnDestroy {
  readonly label = input.required<string>();
  readonly nav = input<'tabs' | 'dots'>('dots');
  /** Milisegundos entre diapositivas; 0 desactiva el avance automático. */
  readonly autoplay = input(0);
  readonly loop = input(true, { transform: booleanAttribute });

  readonly slides = contentChildren(DsSlide);
  readonly index = signal(0);
  readonly paused = signal(false);
  readonly count = computed(() => this.slides().length);

  private readonly document = inject(DOCUMENT);
  private readonly id = ++slidesSequence;
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    effect(() => {
      const slides = this.slides();
      slides.forEach((slide, i) => {
        slide.panelId = `ds-slides-${this.id}-panel-${i}`;
        slide.tabId = `ds-slides-${this.id}-tab-${i}`;
      });
      const active = Math.min(this.index(), Math.max(0, slides.length - 1));
      slides.forEach((slide, i) => slide.active.set(i === active));
    });
    effect((onCleanup) => {
      const every = this.autoplay();
      // Leer el índice reinicia el temporizador tras cada cambio (también los manuales).
      this.index();
      if (!every || this.paused() || this.reducedMotion()) return;
      this.timer = setInterval(() => this.step(1), every);
      onCleanup(() => clearInterval(this.timer));
    });
  }

  step(delta: number): void {
    const count = this.count();
    if (count < 2) return;
    let next = this.index() + delta;
    if (this.loop()) next = (next + count) % count;
    else next = Math.min(count - 1, Math.max(0, next));
    this.go(next, delta > 0 ? 1 : -1);
  }

  go(next: number, direction?: 1 | -1): void {
    const from = this.index();
    if (next === from || next < 0 || next >= this.count()) return;
    const dir = direction ?? (next > from ? 1 : -1);
    this.animate(this.slides()[from], this.slides()[next], dir);
    this.index.set(next);
  }

  onKey(event: KeyboardEvent): void {
    const moves: Record<string, number> = { ArrowRight: 1, ArrowLeft: -1 };
    if (event.key in moves) {
      event.preventDefault();
      this.step(moves[event.key]);
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault();
      this.go(event.key === 'Home' ? 0 : this.count() - 1);
    } else return;
    queueMicrotask(() =>
      this.document.getElementById(this.slides()[this.index()]?.tabId ?? '')?.focus(),
    );
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  /** Entra la nueva desde un lado y sale la anterior por el otro, con Web Animations. */
  private animate(leaving: DsSlide, entering: DsSlide, direction: 1 | -1): void {
    if (this.reducedMotion()) return;
    const out = leaving.element.nativeElement as HTMLElement;
    const into = entering.element.nativeElement as HTMLElement;
    if (typeof into.animate !== 'function') return;
    // Navegación rápida: se cortan las transiciones en curso antes de empezar otra.
    for (const slide of this.slides()) {
      const element = slide.element.nativeElement as HTMLElement;
      element.getAnimations().forEach((animation) => animation.cancel());
      element.classList.remove('is-leaving');
    }
    const styles = this.document.defaultView?.getComputedStyle(into);
    const duration = parseFloat(styles?.getPropertyValue('--ds-duration-slide') || '650') || 650;
    const easing = styles?.getPropertyValue('--ds-ease-out').trim() || 'ease-out';
    const options: KeyframeAnimationOptions = { duration, easing };
    into.animate(
      [
        { transform: `translateX(${direction * 100}%)`, opacity: 0.4 },
        { transform: 'translateX(0)', opacity: 1 },
      ],
      options,
    );
    out.classList.add('is-leaving');
    out
      .animate(
        [
          { transform: 'translateX(0)', opacity: 1 },
          { transform: `translateX(${direction * -100}%)`, opacity: 0.4 },
        ],
        options,
      )
      .finished.then(
        () => out.classList.remove('is-leaving'),
        () => out.classList.remove('is-leaving'),
      );
  }

  private reducedMotion(): boolean {
    return !!this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  }
}
