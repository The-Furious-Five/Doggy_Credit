import {
  Component,
  ElementRef,
  booleanAttribute,
  computed,
  forwardRef,
  input,
  linkedSignal,
  model,
  signal,
  viewChild,
} from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { DsIcon } from './icon';
import { fieldContext, nextId } from './internal';

/**
 * Interruptor on/off (role=switch). El contenido proyectado es su etiqueta.
 * Para decisiones de efecto inmediato; en formularios que se envían, preferir checkbox.
 */
@Component({
  selector: 'ds-switch',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsSwitch), multi: true }],
  host: { class: 'ds-switch', '[class.ds-switch--disabled]': 'isDisabled()' },
  template: `
    <button
      #control
      type="button"
      role="switch"
      class="ds-switch__track"
      [id]="controlId()"
      [attr.aria-checked]="checked()"
      [attr.aria-labelledby]="labelId"
      [disabled]="isDisabled()"
      (click)="toggle()"
      (blur)="touched()"
    >
      <span class="ds-switch__thumb"></span>
    </button>
    <span class="ds-switch__label" [id]="labelId" (click)="toggle(); control.focus()"
      ><ng-content
    /></span>
  `,
})
export class DsSwitch implements ControlValueAccessor {
  readonly checked = model(false);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly id = input('');

  protected readonly labelId = nextId('ds-switch-label');
  private readonly fallbackId = nextId('ds-switch');
  protected readonly controlId = computed(() => this.id() || this.fallbackId);
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  private readonly control = viewChild.required<ElementRef<HTMLButtonElement>>('control');
  private onChange: (value: boolean) => void = () => {};
  private onTouched: () => void = () => {};

  focus(): void {
    this.control().nativeElement.focus();
  }

  protected toggle(): void {
    if (this.isDisabled()) return;
    this.checked.set(!this.checked());
    this.onChange(this.checked());
  }

  protected touched(): void {
    this.onTouched();
  }

  writeValue(value: unknown): void {
    this.checked.set(Boolean(value));
  }
  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.formDisabled.set(disabled);
  }
}

/**
 * Número con botones −/+ propios (patrón ARIA spinbutton), sin las flechas del navegador.
 * Coma decimal. Validadores: dsNumber, dsNumberMin, dsNumberMax.
 */
@Component({
  selector: 'ds-number-field',
  imports: [DsIcon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsNumberField), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => DsNumberField), multi: true },
  ],
  host: {
    class: 'ds-number-field',
    '[class.ds-number-field--invalid]': 'field.invalid() || invalidText()',
    '[class.ds-number-field--disabled]': 'isDisabled()',
  },
  template: `
    <div class="ds-number-field__control">
      <button
        type="button"
        class="ds-adornment"
        tabindex="-1"
        aria-label="Disminuir"
        [disabled]="isDisabled() || atMin()"
        (mousedown)="$event.preventDefault()"
        (click)="stepBy(-1)"
      >
        <ds-icon name="minus" />
      </button>
      @if (prefix()) {
        <span class="ds-number-field__affix" aria-hidden="true">{{ prefix() }}</span>
      }
      <input
        #input
        class="ds-number-field__input"
        type="text"
        role="spinbutton"
        autocomplete="off"
        [attr.inputmode]="decimals() > 0 ? 'decimal' : 'numeric'"
        [id]="field.controlId()"
        [value]="text()"
        [disabled]="isDisabled()"
        [required]="field.required()"
        [attr.aria-label]="field.labelId() ? null : ariaLabel() || null"
        [attr.aria-valuenow]="value()"
        [attr.aria-valuemin]="min()"
        [attr.aria-valuemax]="max()"
        [attr.aria-valuetext]="valueText()"
        [attr.aria-describedby]="field.describedBy()"
        [attr.aria-invalid]="field.invalid() || invalidText() || null"
        (input)="onInput($event)"
        (keydown)="onKeydown($event)"
        (blur)="commitText()"
      />
      @if (suffix()) {
        <span class="ds-number-field__affix" aria-hidden="true">{{ suffix() }}</span>
      }
      <button
        type="button"
        class="ds-adornment"
        tabindex="-1"
        aria-label="Aumentar"
        [disabled]="isDisabled() || atMax()"
        (mousedown)="$event.preventDefault()"
        (click)="stepBy(1)"
      >
        <ds-icon name="plus" />
      </button>
    </div>
  `,
})
export class DsNumberField implements ControlValueAccessor, Validator {
  readonly value = model<number | null>(null);
  readonly min = input<number | null>(null);
  readonly max = input<number | null>(null);
  readonly step = input(1);
  readonly decimals = input(0);
  /** Texto breve antes del número, por ejemplo la moneda. */
  readonly prefix = input('');
  /** Texto breve después del número, por ejemplo la unidad. */
  readonly suffix = input('');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly id = input('');
  readonly ariaLabel = input('', { alias: 'aria-label' });

  protected readonly field = fieldContext(() => this.id(), 'ds-number');
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly invalidText = signal(false);
  protected readonly text = linkedSignal(() => this.format(this.value()));
  protected readonly atMin = computed(() => this.reached(this.min(), -1));
  protected readonly atMax = computed(() => this.reached(this.max(), 1));
  protected readonly valueText = computed(() => {
    const value = this.value();
    if (value === null) return null;
    return [this.prefix(), this.format(value), this.suffix()].filter(Boolean).join(' ');
  });
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');
  private onChange: (value: number | null) => void = () => {};
  private onTouched: () => void = () => {};
  private onValidatorChange: () => void = () => {};

  focus(): void {
    this.input().nativeElement.focus();
  }

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLInputElement).value);
  }

  protected stepBy(direction: number, multiplier = 1): void {
    if (this.isDisabled()) return;
    const current = this.value();
    const next = current === null ? (this.min() ?? 0) : current + direction * this.step() * multiplier;
    this.set(this.clamp(this.round(next)));
  }

  protected onKeydown(event: KeyboardEvent): void {
    const steps: Record<string, [number, number]> = {
      ArrowUp: [1, 1],
      ArrowDown: [-1, 1],
      PageUp: [1, 10],
      PageDown: [-1, 10],
    };
    const step = steps[event.key];
    if (step) {
      event.preventDefault();
      this.commitText(false);
      if (!this.invalidText()) this.stepBy(...step);
      return;
    }
    const min = this.min();
    const max = this.max();
    if (event.key === 'Home' && min !== null) {
      event.preventDefault();
      this.set(min);
    } else if (event.key === 'End' && max !== null) {
      event.preventDefault();
      this.set(max);
    } else if (event.key === 'Enter') this.commitText();
  }

  /** Interpreta lo escrito. Un texto inválido se conserva para poder corregirlo. */
  protected commitText(touch = true): void {
    const raw = this.text();
    const parsed = this.parse(raw);
    if (parsed === undefined) {
      this.invalidText.set(true);
      if (this.value() !== null) {
        this.value.set(null);
        this.onChange(null);
      }
      this.text.set(raw);
      this.onValidatorChange();
    } else this.set(parsed);
    if (touch) this.onTouched();
  }

  private set(value: number | null): void {
    this.invalidText.set(false);
    if (value !== this.value()) {
      this.value.set(value);
      this.onChange(value);
    }
    this.text.set(this.format(value));
    this.onValidatorChange();
  }

  /** `undefined` si el texto no es un número. Admite coma o punto decimal y separador de miles. */
  private parse(text: string): number | null | undefined {
    let value = text.replace(/\s/g, '');
    if (!value) return null;
    if (value.includes(',') && value.includes('.')) value = value.replace(/\./g, '');
    value = value.replace(',', '.');
    return /^-?\d+(\.\d+)?$/.test(value) ? this.round(Number(value)) : undefined;
  }

  private format(value: number | null): string {
    return value === null ? '' : value.toFixed(this.decimals()).replace('.', ',');
  }

  private round(value: number): number {
    const factor = 10 ** this.decimals();
    return Math.round(value * factor) / factor;
  }

  private clamp(value: number): number {
    return Math.min(this.max() ?? Infinity, Math.max(this.min() ?? -Infinity, value));
  }

  private reached(limit: number | null, direction: 1 | -1): boolean {
    const value = this.value();
    return value !== null && limit !== null && direction * value >= direction * limit;
  }

  validate(): ValidationErrors | null {
    if (this.invalidText()) return { dsNumber: true };
    const value = this.value();
    const min = this.min();
    const max = this.max();
    if (value !== null && min !== null && value < min) return { dsNumberMin: { min } };
    if (value !== null && max !== null && value > max) return { dsNumberMax: { max } };
    return null;
  }
  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }
  writeValue(value: unknown): void {
    const number = typeof value === 'number' && Number.isFinite(value) ? value : null;
    this.value.set(number);
    this.text.set(this.format(number));
    this.invalidText.set(false);
  }
  registerOnChange(fn: (value: number | null) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.formDisabled.set(disabled);
  }
}
