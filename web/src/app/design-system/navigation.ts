import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  booleanAttribute,
  computed,
  contentChildren,
  inject,
  input,
  model,
  output,
  viewChild,
} from '@angular/core';
import { DsIcon, DsIconName } from './icon';
import { Typeahead, floatingPanel, matchLabel, nextId } from './internal';
import { DsButton } from './primitives';

export interface DsMenuItem {
  id: string;
  label: string;
  icon?: DsIconName;
  tone?: 'danger';
  disabled?: boolean;
}

/** Menú de acciones (patrón ARIA menu button). No sirve para navegar entre páginas. */
@Component({
  selector: 'ds-menu',
  imports: [DsButton, DsIcon],
  host: { class: 'ds-menu', '(focusout)': 'onFocusOut($event)' },
  template: `
    <button
      #trigger
      dsButton
      aria-haspopup="menu"
      [variant]="variant()"
      [icon]="!!icon()"
      [id]="triggerId"
      [attr.aria-label]="icon() ? label() : null"
      [attr.aria-expanded]="panel.open()"
      [attr.aria-controls]="panel.open() ? menuId : null"
      [disabled]="disabled()"
      (click)="toggle()"
      (keydown)="onTriggerKeydown($event)"
    >
      @if (icon(); as name) {
        <ds-icon [name]="name" />
      } @else {
        {{ label() }}<ds-icon class="ds-select__chevron" name="chevron-down" />
      }
    </button>
    @if (panel.open()) {
      <ul
        #list
        class="ds-popover ds-menu__list"
        role="menu"
        [id]="menuId"
        [attr.aria-labelledby]="triggerId"
        [class.ds-popover--placed]="!!panel.placement()"
        [style.top.px]="panel.placement()?.top"
        [style.left.px]="panel.placement()?.left"
        [style.max-height.px]="panel.placement()?.maxHeight"
        (keydown)="onMenuKeydown($event)"
      >
        @for (item of items(); track item.id) {
          <li
            role="menuitem"
            class="ds-menu__item"
            tabindex="-1"
            [attr.aria-disabled]="item.disabled || null"
            [attr.data-tone]="item.tone || null"
            (click)="activate(item)"
          >
            @if (item.icon; as name) {
              <ds-icon [name]="name" />
            }
            <span>{{ item.label }}</span>
          </li>
        }
      </ul>
    }
  `,
})
export class DsMenu {
  /** Texto del botón, o su nombre accesible cuando se usa `icon`. */
  readonly label = input.required<string>();
  readonly items = input.required<readonly DsMenuItem[]>();
  /** Muestra un botón circular solo con este icono. */
  readonly icon = input<DsIconName | null>(null);
  readonly variant = input<'primary' | 'secondary' | 'quiet'>('secondary');
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Emite el `id` de la acción elegida. */
  readonly itemSelected = output<string>();

  protected readonly triggerId = nextId('ds-menu-trigger');
  protected readonly menuId = nextId('ds-menu');
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  protected readonly panel = floatingPanel(
    () => this.trigger().nativeElement,
    () => this.list()?.nativeElement,
  );
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly typeahead = new Typeahead();

  protected toggle(): void {
    if (this.panel.open()) this.close(false);
    else this.open(0);
  }

  protected onTriggerKeydown(event: KeyboardEvent): void {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    event.preventDefault();
    this.open(event.key === 'ArrowDown' ? 0 : this.items().length - 1);
  }

  protected onMenuKeydown(event: KeyboardEvent): void {
    const current = this.menuItems().indexOf(this.host.ownerDocument.activeElement as HTMLElement);
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.focusItem(current + 1);
        return;
      case 'ArrowUp':
        event.preventDefault();
        this.focusItem(current - 1);
        return;
      case 'Home':
        event.preventDefault();
        this.focusItem(0);
        return;
      case 'End':
        event.preventDefault();
        this.focusItem(this.items().length - 1);
        return;
      case 'Escape':
        event.preventDefault();
        this.close(true);
        return;
      case 'Tab':
        this.close(false);
        return;
      case 'Enter':
      case ' ': {
        event.preventDefault();
        const item = this.items()[current];
        if (item) this.activate(item);
        return;
      }
      default:
        if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
          const query = this.typeahead.push(event.key);
          const labels = this.items().map((item) => item.label);
          const index = matchLabel(labels, query, current + (query.length === 1 ? 1 : 0));
          if (index >= 0) this.focusItem(index);
        }
    }
  }

  protected activate(item: DsMenuItem): void {
    if (item.disabled) return;
    this.close(true);
    this.itemSelected.emit(item.id);
  }

  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (this.panel.open() && next && !this.host.contains(next)) this.close(false);
  }

  private open(index: number): void {
    if (this.disabled()) return;
    this.panel.show();
    this.cdr.detectChanges();
    this.panel.place();
    // El panel debe estar visible antes de recibir el foco: el navegador no enfoca lo oculto.
    this.cdr.detectChanges();
    this.focusItem(index);
  }

  private close(returnFocus: boolean): void {
    this.panel.hide();
    this.typeahead.reset();
    if (returnFocus) this.trigger().nativeElement.focus();
  }

  private menuItems(): HTMLElement[] {
    return Array.from(
      this.list()?.nativeElement.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [],
    );
  }

  private focusItem(index: number): void {
    const items = this.menuItems();
    if (items.length) items[(index + items.length) % items.length].focus();
  }
}

/** Panel de una pestaña. Debe estar dentro de ds-tabs. */
@Component({
  selector: 'ds-tab',
  host: {
    class: 'ds-tab-panel',
    role: 'tabpanel',
    tabindex: '0',
    '[id]': 'panelId()',
    '[attr.aria-labelledby]': 'tabId()',
    '[hidden]': '!active()',
  },
  template: '<ng-content />',
})
export class DsTab {
  readonly value = input.required<string>();
  readonly label = input.required<string>();
  private readonly tabs = inject(DsTabs);
  readonly tabId = computed(() => `${this.tabs.baseId}-tab-${this.value()}`);
  readonly panelId = computed(() => `${this.tabs.baseId}-panel-${this.value()}`);
  readonly active = computed(() => this.tabs.current() === this.value());
}

/** Pestañas con activación automática: flechas, Inicio y Fin. */
@Component({
  selector: 'ds-tabs',
  host: { class: 'ds-tabs' },
  template: `
    <div
      class="ds-tabs__list"
      role="tablist"
      [attr.aria-label]="label()"
      (keydown)="onKeydown($event)"
    >
      @for (tab of tabs(); track tab.value()) {
        <button
          type="button"
          role="tab"
          class="ds-tabs__tab"
          [id]="tab.tabId()"
          [attr.aria-selected]="tab.active()"
          [attr.aria-controls]="tab.panelId()"
          [attr.tabindex]="tab.active() ? 0 : -1"
          (click)="selected.set(tab.value())"
        >
          {{ tab.label() }}
        </button>
      }
    </div>
    <ng-content />
  `,
})
export class DsTabs {
  readonly label = input.required<string>();
  readonly selected = model('');
  readonly tabs = contentChildren(DsTab);
  readonly baseId = nextId('ds-tabs');
  /** Pestaña visible: la elegida si existe; si no, la primera. */
  readonly current = computed(() => {
    const values = this.tabs().map((tab) => tab.value());
    return values.includes(this.selected()) ? this.selected() : (values[0] ?? '');
  });
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly cdr = inject(ChangeDetectorRef);

  protected onKeydown(event: KeyboardEvent): void {
    const tabs = this.tabs();
    const index = tabs.findIndex((tab) => tab.active());
    const targets: Record<string, number> = {
      ArrowRight: index + 1,
      ArrowLeft: index - 1,
      Home: 0,
      End: tabs.length - 1,
    };
    const target = targets[event.key];
    if (target === undefined || !tabs.length) return;
    event.preventDefault();
    const tab = tabs[(target + tabs.length) % tabs.length];
    this.selected.set(tab.value());
    this.cdr.detectChanges();
    this.host.ownerDocument.getElementById(tab.tabId())?.focus();
  }
}

/** Paginación de listados: resumen visible y página actual con aria-current. */
@Component({
  selector: 'ds-pagination',
  imports: [DsButton, DsIcon],
  host: { class: 'ds-pagination' },
  template: `
    <p class="ds-pagination__summary" aria-live="polite">{{ summary() }}</p>
    <nav [attr.aria-label]="label()">
      <ul class="ds-pagination__list">
        <li>
          <button
            dsButton
            variant="quiet"
            icon
            aria-label="Página anterior"
            [disabled]="current() <= 1"
            (click)="go(current() - 1)"
          >
            <ds-icon name="chevron-left" />
          </button>
        </li>
        @for (item of items(); track $index) {
          <li>
            @if (item === 0) {
              <span class="ds-pagination__gap" aria-hidden="true">…</span>
            } @else {
              <button
                dsButton
                class="ds-pagination__page"
                [variant]="item === current() ? 'primary' : 'quiet'"
                [attr.aria-current]="item === current() ? 'page' : null"
                [attr.aria-label]="'Página ' + item"
                (click)="go(item)"
              >
                {{ item }}
              </button>
            }
          </li>
        }
        <li>
          <button
            dsButton
            variant="quiet"
            icon
            aria-label="Página siguiente"
            [disabled]="current() >= pageCount()"
            (click)="go(current() + 1)"
          >
            <ds-icon name="chevron" />
          </button>
        </li>
      </ul>
    </nav>
  `,
})
export class DsPagination {
  readonly total = input.required<number>();
  readonly pageSize = input(10);
  /** Página actual, desde 1. */
  readonly page = model(1);
  readonly label = input('Paginación');

  protected readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.total() / Math.max(1, this.pageSize()))),
  );
  protected readonly current = computed(() =>
    Math.min(this.pageCount(), Math.max(1, this.page())),
  );
  /** Números de página; 0 representa un salto. */
  protected readonly items = computed(() => {
    const count = this.pageCount();
    const page = this.current();
    const range = (from: number, to: number) =>
      Array.from({ length: to - from + 1 }, (_, i) => from + i);
    if (count <= 7) return range(1, count);
    const start = Math.max(2, Math.min(page - 1, count - 4));
    const end = Math.min(count - 1, Math.max(page + 1, 5));
    return [1, ...(start > 2 ? [0] : []), ...range(start, end), ...(end < count - 1 ? [0] : []), count];
  });
  protected readonly summary = computed(() => {
    const total = this.total();
    if (!total) return 'Sin resultados';
    const first = (this.current() - 1) * this.pageSize() + 1;
    return `${first}–${Math.min(total, first + this.pageSize() - 1)} de ${total}`;
  });

  protected go(page: number): void {
    this.page.set(Math.min(this.pageCount(), Math.max(1, page)));
  }
}

/** Progreso de un flujo por pasos. `current` empieza en 0. */
@Component({
  selector: 'ds-steps',
  imports: [DsIcon],
  host: { class: 'ds-steps' },
  template: `
    <ol class="ds-steps__list" [attr.aria-label]="label()">
      @for (step of steps(); track $index) {
        <li
          class="ds-steps__item"
          [attr.data-state]="state($index)"
          [attr.aria-current]="$index === current() ? 'step' : null"
        >
          <span class="ds-steps__marker" aria-hidden="true">
            @if (state($index) === 'complete') {
              <ds-icon name="check" />
            } @else {
              {{ $index + 1 }}
            }
          </span>
          <span>{{ step }}</span>
          @if (state($index) === 'complete') {
            <span class="ds-sr-only">(completado)</span>
          }
        </li>
      }
    </ol>
  `,
})
export class DsSteps {
  readonly steps = input.required<readonly string[]>();
  readonly current = input(0);
  readonly label = input('Progreso');

  protected state(index: number): 'complete' | 'current' | 'upcoming' {
    const current = this.current();
    return index < current ? 'complete' : index === current ? 'current' : 'upcoming';
  }
}
