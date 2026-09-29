import {
  ChangeDetectorRef,
  Component,
  ElementRef,
  booleanAttribute,
  computed,
  forwardRef,
  inject,
  input,
  linkedSignal,
  model,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DsIcon } from './icon';
import {
  Typeahead,
  enabledIndex,
  fieldContext,
  floatingPanel,
  matchLabel,
  nextId,
  normalize,
} from './internal';

export interface DsOption<T = unknown> {
  value: T;
  label: string;
  description?: string;
  disabled?: boolean;
}

const PAGE = 10;
const printable = (event: KeyboardEvent) =>
  event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey;

/**
 * Lista desplegable propia (patrón ARIA "select-only combobox" + listbox).
 * No usa el menú del navegador: el foco queda en el control y la opción activa
 * se comunica con aria-activedescendant.
 */
@Component({
  selector: 'ds-select',
  imports: [DsIcon],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsSelect), multi: true }],
  host: { class: 'ds-select', '[class.ds-select--open]': 'panel.open()' },
  template: `
    <div
      #trigger
      class="ds-control ds-select__trigger"
      role="combobox"
      aria-haspopup="listbox"
      [id]="field.controlId()"
      [attr.tabindex]="isDisabled() ? -1 : 0"
      [attr.aria-expanded]="panel.open()"
      [attr.aria-controls]="panel.open() ? listId : null"
      [attr.aria-labelledby]="field.labelId()"
      [attr.aria-label]="field.labelId() ? null : ariaLabel() || null"
      [attr.aria-describedby]="field.describedBy()"
      [attr.aria-invalid]="field.invalid() || null"
      [attr.aria-required]="field.required() || null"
      [attr.aria-disabled]="isDisabled() || null"
      [attr.aria-activedescendant]="panel.open() && active() >= 0 ? optionId(active()) : null"
      (click)="toggle()"
      (keydown)="onKeydown($event)"
      (blur)="onBlur()"
    >
      <span class="ds-select__value" [class.ds-select__placeholder]="!hasValue()">{{
        display()
      }}</span>
      <ds-icon class="ds-select__chevron" name="chevron-down" />
    </div>
    @if (panel.open()) {
      <div
        #list
        class="ds-popover"
        [class.ds-popover--placed]="!!panel.placement()"
        [style.top.px]="panel.placement()?.top"
        [style.left.px]="panel.placement()?.left"
        [style.min-width.px]="panel.placement()?.minWidth"
        [style.max-height.px]="panel.placement()?.maxHeight"
        (mousedown)="$event.preventDefault()"
      >
        @if (options().length) {
          <ul
            class="ds-listbox"
            role="listbox"
            [id]="listId"
            [attr.aria-labelledby]="field.labelId()"
            [attr.aria-label]="field.labelId() ? null : ariaLabel() || null"
            [attr.aria-multiselectable]="multiple() || null"
          >
            @for (option of options(); track $index) {
              <li
                role="option"
                class="ds-option"
                [id]="optionId($index)"
                [class.ds-option--active]="$index === active()"
                [attr.aria-selected]="isSelected(option)"
                [attr.aria-disabled]="option.disabled || null"
                (click)="choose($index)"
                (pointermove)="option.disabled || active.set($index)"
              >
                @if (multiple()) {
                  <span class="ds-option__box" aria-hidden="true"><ds-icon name="check" /></span>
                }
                <span class="ds-option__text">
                  <span>{{ option.label }}</span>
                  @if (option.description) {
                    <span class="ds-option__description">{{ option.description }}</span>
                  }
                </span>
                @if (!multiple()) {
                  <ds-icon class="ds-option__check" name="check" />
                }
              </li>
            }
          </ul>
        } @else {
          <p class="ds-listbox__empty">No hay opciones disponibles.</p>
        }
      </div>
    }
  `,
})
export class DsSelect implements ControlValueAccessor {
  readonly options = input.required<readonly DsOption[]>();
  /** Valor elegido; con `multiple`, un arreglo de valores. */
  readonly value = model<unknown>(null);
  readonly multiple = input(false, { transform: booleanAttribute });
  readonly placeholder = input('Selecciona una opción');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly id = input('');
  /** Nombre accesible cuando el control no está dentro de ds-field. */
  readonly ariaLabel = input('', { alias: 'aria-label' });

  protected readonly field = fieldContext(() => this.id(), 'ds-select');
  protected readonly listId = nextId('ds-listbox');
  protected readonly active = signal(-1);
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  private readonly trigger = viewChild.required<ElementRef<HTMLElement>>('trigger');
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  protected readonly panel = floatingPanel(
    () => this.trigger().nativeElement,
    () => this.list()?.nativeElement,
  );
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly typeahead = new Typeahead();
  private onChange: (value: unknown) => void = () => {};
  private onTouched: () => void = () => {};

  private readonly selectedValues = computed<unknown[]>(() => {
    const value = this.value();
    if (this.multiple()) return Array.isArray(value) ? value : [];
    return value === null || value === undefined ? [] : [value];
  });
  protected readonly hasValue = computed(() => this.options().some((o) => this.isSelected(o)));
  protected readonly display = computed(() => {
    const labels = this.options()
      .filter((o) => this.isSelected(o))
      .map((o) => o.label);
    if (!labels.length) return this.placeholder();
    return labels.length > 2 ? `${labels.length} opciones seleccionadas` : labels.join(', ');
  });

  protected isSelected(option: DsOption): boolean {
    return this.selectedValues().some((value) => Object.is(value, option.value));
  }
  protected optionId(index: number): string {
    return `${this.listId}-${index}`;
  }

  focus(): void {
    this.trigger().nativeElement.focus();
  }

  protected toggle(): void {
    if (this.panel.open()) this.close();
    else this.open();
  }

  private open(position: 'selected' | 'first' | 'last' = 'selected'): void {
    if (this.isDisabled() || this.panel.open()) return;
    const options = this.options();
    const selected = options.findIndex((o) => this.isSelected(o) && !o.disabled);
    this.active.set(
      position === 'last'
        ? enabledIndex(options, options.length - 1, -1)
        : position === 'selected' && selected >= 0
          ? selected
          : enabledIndex(options, 0, 1),
    );
    this.panel.show();
    this.cdr.detectChanges();
    this.panel.place();
    this.cdr.detectChanges();
    this.reveal();
  }

  private close(): void {
    this.panel.hide();
    this.typeahead.reset();
  }

  protected choose(index: number): void {
    const option = this.options()[index];
    if (!option || option.disabled) return;
    if (this.multiple()) {
      const toggled = !this.isSelected(option);
      this.commit(
        this.options()
          .filter((o) => (o === option ? toggled : this.isSelected(o)))
          .map((o) => o.value),
      );
      this.active.set(index);
    } else {
      this.commit(option.value);
      this.close();
    }
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (this.isDisabled()) return;
    const open = this.panel.open();
    const options = this.options();
    const current = this.active();
    const move = (index: number) => {
      if (index < 0) return;
      this.active.set(index);
      this.reveal();
    };
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!open) this.open();
        else if (!event.altKey) move(enabledIndex(options, current + 1, 1));
        return;
      case 'ArrowUp':
        event.preventDefault();
        if (!open) this.open();
        else if (event.altKey) {
          if (!this.multiple()) this.choose(current);
          this.close();
        } else move(enabledIndex(options, current < 0 ? options.length - 1 : current - 1, -1));
        return;
      case 'Home':
        event.preventDefault();
        if (open) move(enabledIndex(options, 0, 1));
        else this.open('first');
        return;
      case 'End':
        event.preventDefault();
        if (open) move(enabledIndex(options, options.length - 1, -1));
        else this.open('last');
        return;
      case 'PageDown':
        if (!open) return;
        event.preventDefault();
        move(enabledIndex(options, Math.min(options.length - 1, current + PAGE), -1));
        return;
      case 'PageUp':
        if (!open) return;
        event.preventDefault();
        move(enabledIndex(options, Math.max(0, current - PAGE), 1));
        return;
      case ' ':
        if (this.typeahead.active) {
          event.preventDefault();
          this.search(' ');
          return;
        }
        event.preventDefault();
        if (!open) this.open();
        else this.choose(current);
        return;
      case 'Enter':
        event.preventDefault();
        if (!open) this.open();
        else this.choose(current);
        return;
      case 'Escape':
        if (!open) return;
        event.preventDefault();
        this.close();
        return;
      case 'Tab':
        if (!open) return;
        if (!this.multiple()) this.choose(current);
        this.close();
        return;
      default:
        if (printable(event)) {
          event.preventDefault();
          this.search(event.key);
        }
    }
  }

  protected onBlur(): void {
    this.close();
    this.onTouched();
  }

  private search(char: string): void {
    const query = this.typeahead.push(char);
    const options = this.options();
    const from = this.panel.open()
      ? this.active()
      : options.findIndex((o) => this.isSelected(o));
    const index = matchLabel(
      options.map((o) => o.label),
      query,
      from + (query.length === 1 ? 1 : 0),
      (i) => !!options[i].disabled,
    );
    if (index < 0) return;
    if (!this.panel.open()) this.open();
    this.active.set(index);
    this.reveal();
  }

  private reveal(): void {
    const id = this.active() >= 0 ? this.optionId(this.active()) : '';
    this.list()?.nativeElement.querySelector(`#${id}`)?.scrollIntoView?.({ block: 'nearest' });
  }

  private commit(value: unknown): void {
    this.value.set(value);
    this.onChange(value);
  }

  writeValue(value: unknown): void {
    this.value.set(value ?? null);
  }
  registerOnChange(fn: (value: unknown) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.formDisabled.set(disabled);
    if (disabled) this.close();
  }
}

/**
 * Campo de texto con lista filtrada (patrón ARIA combobox con autocompletado de lista).
 * Al salir del campo solo conserva una opción válida.
 */
@Component({
  selector: 'ds-combobox',
  imports: [DsIcon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsCombobox), multi: true },
  ],
  host: { class: 'ds-combobox', '[class.ds-combobox--open]': 'panel.open()' },
  template: `
    <div #anchor class="ds-combobox__control">
      <input
        #input
        class="ds-control ds-combobox__input"
        type="text"
        role="combobox"
        autocomplete="off"
        spellcheck="false"
        aria-autocomplete="list"
        [id]="field.controlId()"
        [value]="query()"
        [placeholder]="placeholder()"
        [disabled]="isDisabled()"
        [required]="field.required()"
        [attr.aria-label]="field.labelId() ? null : ariaLabel() || null"
        [attr.aria-expanded]="panel.open()"
        [attr.aria-controls]="panel.open() && filtered().length ? listId : null"
        [attr.aria-activedescendant]="panel.open() && active() >= 0 ? optionId(active()) : null"
        [attr.aria-describedby]="field.describedBy()"
        [attr.aria-invalid]="field.invalid() || null"
        (input)="onInput($event)"
        (keydown)="onKeydown($event)"
        (click)="open()"
        (blur)="onBlur()"
      />
      <button
        type="button"
        class="ds-adornment ds-adornment--end"
        tabindex="-1"
        aria-label="Mostrar opciones"
        [attr.aria-expanded]="panel.open()"
        [disabled]="isDisabled()"
        (mousedown)="$event.preventDefault()"
        (click)="toggleFromButton()"
      >
        <ds-icon class="ds-select__chevron" name="chevron-down" />
      </button>
    </div>
    <span class="ds-sr-only" aria-live="polite">{{ status() }}</span>
    @if (panel.open()) {
      <div
        #list
        class="ds-popover"
        [class.ds-popover--placed]="!!panel.placement()"
        [style.top.px]="panel.placement()?.top"
        [style.left.px]="panel.placement()?.left"
        [style.min-width.px]="panel.placement()?.minWidth"
        [style.max-height.px]="panel.placement()?.maxHeight"
        (mousedown)="$event.preventDefault()"
      >
        @if (filtered().length) {
          <ul
            class="ds-listbox"
            role="listbox"
            [id]="listId"
            [attr.aria-labelledby]="field.labelId()"
            [attr.aria-label]="field.labelId() ? null : ariaLabel() || null"
          >
            @for (option of filtered(); track $index) {
              <li
                role="option"
                class="ds-option"
                [id]="optionId($index)"
                [class.ds-option--active]="$index === active()"
                [attr.aria-selected]="isSelected(option)"
                [attr.aria-disabled]="option.disabled || null"
                (click)="choose($index)"
                (pointermove)="option.disabled || active.set($index)"
              >
                <span class="ds-option__text">
                  <span>{{ option.label }}</span>
                  @if (option.description) {
                    <span class="ds-option__description">{{ option.description }}</span>
                  }
                </span>
                <ds-icon class="ds-option__check" name="check" />
              </li>
            }
          </ul>
        } @else {
          <p class="ds-listbox__empty">Sin resultados para «{{ query() }}».</p>
        }
      </div>
    }
  `,
})
export class DsCombobox implements ControlValueAccessor {
  readonly options = input.required<readonly DsOption[]>();
  readonly value = model<unknown>(null);
  readonly placeholder = input('Escribe para buscar');
  /** Desactivar para búsquedas remotas: la pantalla actualiza `options` con `(queryChange)`. */
  readonly filter = input(true, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly id = input('');
  readonly ariaLabel = input('', { alias: 'aria-label' });
  readonly queryChange = output<string>();

  protected readonly field = fieldContext(() => this.id(), 'ds-combobox');
  protected readonly listId = nextId('ds-listbox');
  protected readonly active = signal(-1);
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  private readonly anchor = viewChild.required<ElementRef<HTMLElement>>('anchor');
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  protected readonly panel = floatingPanel(
    () => this.anchor().nativeElement,
    () => this.list()?.nativeElement,
  );
  private readonly cdr = inject(ChangeDetectorRef);
  private onChange: (value: unknown) => void = () => {};
  private onTouched: () => void = () => {};

  private readonly selectedLabel = computed(
    () => this.options().find((o) => Object.is(o.value, this.value()))?.label ?? '',
  );
  /** Texto del campo: sigue a la opción elegida y la persona puede sobrescribirlo. */
  protected readonly query = linkedSignal(() => this.selectedLabel());
  protected readonly filtered = computed(() => {
    const query = this.query();
    const wanted = normalize(query.trim());
    if (!this.filter() || !wanted || query === this.selectedLabel()) return this.options();
    return this.options().filter((o) => normalize(o.label).includes(wanted));
  });
  protected readonly status = computed(() => {
    if (!this.panel.open()) return '';
    const count = this.filtered().length;
    if (!count) return 'Sin resultados';
    return count === 1 ? '1 resultado disponible' : `${count} resultados disponibles`;
  });

  protected isSelected(option: DsOption): boolean {
    return Object.is(option.value, this.value());
  }
  protected optionId(index: number): string {
    return `${this.listId}-${index}`;
  }

  focus(): void {
    this.input().nativeElement.focus();
  }

  protected open(): void {
    if (this.isDisabled() || this.panel.open()) return;
    this.active.set(-1);
    this.panel.show();
    this.refresh();
  }

  private close(): void {
    this.panel.hide();
  }

  protected toggleFromButton(): void {
    if (this.panel.open()) this.close();
    else {
      this.open();
      this.focus();
    }
  }

  protected onInput(event: Event): void {
    const text = (event.target as HTMLInputElement).value;
    this.query.set(text);
    this.queryChange.emit(text);
    this.active.set(-1);
    if (this.panel.open()) this.refresh();
    else this.open();
  }

  protected onKeydown(event: KeyboardEvent): void {
    const open = this.panel.open();
    const options = this.filtered();
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        if (!open) {
          this.open();
          if (!event.altKey) this.move(enabledIndex(this.filtered(), 0, 1));
        } else this.move(enabledIndex(options, this.active() + 1, 1));
        return;
      case 'ArrowUp': {
        event.preventDefault();
        if (!open) this.open();
        const from = this.active() < 0 ? this.filtered().length - 1 : this.active() - 1;
        this.move(enabledIndex(this.filtered(), from, -1));
        return;
      }
      case 'Enter':
        if (open && this.active() >= 0) {
          event.preventDefault();
          this.choose(this.active());
        }
        return;
      case 'Escape':
        if (open) {
          event.preventDefault();
          this.close();
        } else if (this.query()) {
          event.preventDefault();
          this.query.set('');
          this.commit(null);
        }
        return;
      case 'Tab':
        this.close();
    }
  }

  protected choose(index: number): void {
    const option = this.filtered()[index];
    if (!option || option.disabled) return;
    this.commit(option.value);
    this.query.set(option.label);
    this.close();
  }

  protected onBlur(): void {
    this.close();
    const wanted = normalize(this.query().trim());
    if (!wanted) {
      if (this.value() !== null) this.commit(null);
    } else {
      const match = this.options().find((o) => !o.disabled && normalize(o.label) === wanted);
      if (match && !Object.is(match.value, this.value())) this.commit(match.value);
      this.query.set(match?.label ?? this.selectedLabel());
    }
    this.onTouched();
  }

  private move(index: number): void {
    if (index < 0) return;
    this.active.set(index);
    this.list()
      ?.nativeElement.querySelector(`#${this.optionId(index)}`)
      ?.scrollIntoView?.({ block: 'nearest' });
  }

  /** La lista cambia de tamaño al filtrar: se vuelve a colocar. */
  private refresh(): void {
    this.cdr.detectChanges();
    this.panel.place();
    this.cdr.detectChanges();
  }

  private commit(value: unknown): void {
    this.value.set(value);
    this.onChange(value);
  }

  writeValue(value: unknown): void {
    this.value.set(value ?? null);
    this.query.set(this.selectedLabel());
  }
  registerOnChange(fn: (value: unknown) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.formDisabled.set(disabled);
    if (disabled) this.close();
  }
}
