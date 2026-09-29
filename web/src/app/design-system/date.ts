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
import { fieldContext, floatingPanel, nextId } from './internal';
import { DsButton } from './primitives';

// Fechas en hora local; el valor público siempre es ISO `aaaa-mm-dd`.
const MONTHS = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];
const WEEKDAYS = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
const WEEKDAY_INITIALS = ['L', 'M', 'X', 'J', 'V', 'S', 'D'];

const pad = (n: number) => String(n).padStart(2, '0');
const weekday = (date: Date) => (date.getDay() + 6) % 7;
const addDays = (date: Date, days: number) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
const toIso = (date: Date) =>
  `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const toDisplay = (date: Date) =>
  `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()}`;
const toLong = (date: Date) =>
  `${WEEKDAYS[weekday(date)]}, ${date.getDate()} de ${MONTHS[date.getMonth()]} de ${date.getFullYear()}`;

function today(): Date {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function addMonths(date: Date, months: number): Date {
  const first = new Date(date.getFullYear(), date.getMonth() + months, 1);
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  return new Date(first.getFullYear(), first.getMonth(), Math.min(date.getDate(), last));
}

function build(year: number, month: number, day: number): Date | null {
  const date = new Date(year, month - 1, day);
  const valid =
    date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
  return valid ? date : null;
}

function parseIso(value: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '');
  return match ? build(+match[1], +match[2], +match[3]) : null;
}

/** Acepta dd/mm/aaaa (también con - o .) y aaaa-mm-dd. */
function parseDisplay(text: string): Date | null {
  const value = text.trim();
  const match = /^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/.exec(value);
  return match ? build(+match[3], +match[2], +match[1]) : parseIso(value);
}

interface CalendarDay {
  date: Date;
  iso: string;
  day: number;
  label: string;
  selected: boolean;
  today: boolean;
  disabled: boolean;
  focus: boolean;
}

/** Mes navegable con teclado (patrón ARIA de cuadrícula de fechas). Semana desde el lunes. */
@Component({
  selector: 'ds-calendar',
  imports: [DsButton, DsIcon],
  host: { class: 'ds-calendar' },
  template: `
    <div class="ds-calendar__header">
      <button
        dsButton
        variant="quiet"
        icon
        aria-label="Mes anterior"
        [disabled]="!canShow(-1)"
        (click)="showMonth(-1)"
      >
        <ds-icon name="chevron-left" />
      </button>
      <p class="ds-calendar__title" [id]="titleId" aria-live="polite">{{ title() }}</p>
      <button
        dsButton
        variant="quiet"
        icon
        aria-label="Mes siguiente"
        [disabled]="!canShow(1)"
        (click)="showMonth(1)"
      >
        <ds-icon name="chevron" />
      </button>
    </div>
    <table
      class="ds-calendar__grid"
      role="grid"
      [attr.aria-labelledby]="titleId"
      (keydown)="onKeydown($event)"
    >
      <thead>
        <tr>
          @for (name of weekdays; track name; let i = $index) {
            <th scope="col">
              <span aria-hidden="true">{{ initials[i] }}</span
              ><span class="ds-sr-only">{{ name }}</span>
            </th>
          }
        </tr>
      </thead>
      <tbody>
        @for (week of weeks(); track $index) {
          <tr>
            @for (day of week; track $index) {
              <td [attr.aria-selected]="day ? day.selected : null">
                @if (day) {
                  <button
                    type="button"
                    class="ds-calendar__day"
                    [class.is-selected]="day.selected"
                    [class.is-today]="day.today"
                    [attr.data-date]="day.iso"
                    [attr.tabindex]="day.focus ? 0 : -1"
                    [attr.aria-label]="day.label"
                    [attr.aria-current]="day.today ? 'date' : null"
                    [attr.aria-disabled]="day.disabled || null"
                    (click)="select(day)"
                  >
                    {{ day.day }}
                  </button>
                }
              </td>
            }
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class DsCalendar {
  /** Fecha elegida en ISO `aaaa-mm-dd`. */
  readonly value = model<string | null>(null);
  readonly min = input<string | null>(null);
  readonly max = input<string | null>(null);

  protected readonly titleId = nextId('ds-calendar');
  protected readonly weekdays = WEEKDAYS;
  protected readonly initials = WEEKDAY_INITIALS;
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly minDate = computed(() => parseIso(this.min()));
  private readonly maxDate = computed(() => parseIso(this.max()));
  /** Día con el foco itinerante; sigue a la fecha elegida. */
  protected readonly focused = linkedSignal(() => this.clamp(parseIso(this.value()) ?? today()));

  protected readonly title = computed(() => {
    const focused = this.focused();
    const month = MONTHS[focused.getMonth()];
    return `${month.charAt(0).toUpperCase()}${month.slice(1)} de ${focused.getFullYear()}`;
  });

  protected readonly weeks = computed(() => {
    const focused = toIso(this.focused());
    const selected = this.value();
    const now = toIso(today());
    const first = new Date(this.focused().getFullYear(), this.focused().getMonth(), 1);
    const length = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const cells: (CalendarDay | null)[] = Array(weekday(first)).fill(null);
    for (let day = 1; day <= length; day++) {
      const date = new Date(first.getFullYear(), first.getMonth(), day);
      const iso = toIso(date);
      cells.push({
        date,
        iso,
        day,
        label: toLong(date),
        selected: iso === selected,
        today: iso === now,
        disabled: this.outOfRange(date),
        focus: iso === focused,
      });
    }
    while (cells.length % 7) cells.push(null);
    return Array.from({ length: cells.length / 7 }, (_, w) => cells.slice(w * 7, w * 7 + 7));
  });

  /** Lleva el foco del teclado al día activo (lo usa ds-date-picker al abrirse). */
  focusDay(): void {
    this.cdr.detectChanges();
    this.host
      .querySelector<HTMLElement>(`[data-date="${toIso(this.focused())}"]`)
      ?.focus();
  }

  protected canShow(step: 1 | -1): boolean {
    const focused = this.focused();
    if (step < 0) {
      const min = this.minDate();
      return !min || new Date(focused.getFullYear(), focused.getMonth(), 0) >= min;
    }
    const max = this.maxDate();
    return !max || new Date(focused.getFullYear(), focused.getMonth() + 1, 1) <= max;
  }

  protected showMonth(step: 1 | -1): void {
    this.focused.set(this.clamp(addMonths(this.focused(), step)));
  }

  protected select(day: CalendarDay): void {
    if (day.disabled) return;
    this.focused.set(day.date);
    this.value.set(day.iso);
  }

  protected onKeydown(event: KeyboardEvent): void {
    const focused = this.focused();
    const years = event.shiftKey ? 12 : 1;
    const moves: Record<string, () => Date> = {
      ArrowLeft: () => addDays(focused, -1),
      ArrowRight: () => addDays(focused, 1),
      ArrowUp: () => addDays(focused, -7),
      ArrowDown: () => addDays(focused, 7),
      Home: () => addDays(focused, -weekday(focused)),
      End: () => addDays(focused, 6 - weekday(focused)),
      PageUp: () => addMonths(focused, -years),
      PageDown: () => addMonths(focused, years),
    };
    const next = moves[event.key];
    if (!next) return;
    event.preventDefault();
    this.focused.set(this.clamp(next()));
    this.focusDay();
  }

  private outOfRange(date: Date): boolean {
    const min = this.minDate();
    const max = this.maxDate();
    return (!!min && date < min) || (!!max && date > max);
  }

  private clamp(date: Date): Date {
    const min = this.minDate();
    const max = this.maxDate();
    if (min && date < min) return min;
    if (max && date > max) return max;
    return date;
  }
}

/**
 * Fecha escrita (dd/mm/aaaa) o elegida en un calendario propio, sin el selector del navegador.
 * Valor ISO `aaaa-mm-dd` o null. Validadores: dsDate, dsDateMin, dsDateMax.
 */
@Component({
  selector: 'ds-date-picker',
  imports: [DsCalendar, DsIcon],
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => DsDatePicker), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => DsDatePicker), multi: true },
  ],
  host: { class: 'ds-date-picker', '(focusout)': 'onFocusOut($event)' },
  template: `
    <div #anchor class="ds-date-picker__control">
      <input
        #input
        class="ds-control ds-date-picker__input"
        type="text"
        inputmode="numeric"
        autocomplete="off"
        placeholder="dd/mm/aaaa"
        [id]="field.controlId()"
        [value]="text()"
        [disabled]="isDisabled()"
        [required]="field.required()"
        [attr.aria-label]="field.labelId() ? null : ariaLabel() || null"
        [attr.aria-describedby]="describedBy()"
        [attr.aria-invalid]="field.invalid() || invalidText() || null"
        (input)="onInput($event)"
        (keydown)="onInputKeydown($event)"
        (blur)="commitText()"
      />
      <button
        #toggleButton
        type="button"
        class="ds-adornment ds-adornment--end"
        aria-haspopup="dialog"
        [attr.aria-label]="toggleLabel()"
        [attr.aria-expanded]="panel.open()"
        [attr.aria-controls]="panel.open() ? dialogId : null"
        [disabled]="isDisabled()"
        (click)="toggle()"
      >
        <ds-icon name="calendar" />
      </button>
    </div>
    <span class="ds-sr-only" [id]="formatId">Formato día/mes/año.</span>
    @if (panel.open()) {
      <div
        #dialog
        class="ds-popover ds-date-picker__panel"
        role="dialog"
        aria-label="Elegir fecha"
        [id]="dialogId"
        [class.ds-popover--placed]="!!panel.placement()"
        [style.top.px]="panel.placement()?.top"
        [style.left.px]="panel.placement()?.left"
        [style.max-height.px]="panel.placement()?.maxHeight"
        (keydown.escape)="onEscape($event)"
      >
        <ds-calendar [value]="value()" [min]="min()" [max]="max()" (valueChange)="pick($event)" />
      </div>
    }
  `,
})
export class DsDatePicker implements ControlValueAccessor, Validator {
  readonly value = model<string | null>(null);
  readonly min = input<string | null>(null);
  readonly max = input<string | null>(null);
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly id = input('');
  readonly ariaLabel = input('', { alias: 'aria-label' });

  protected readonly field = fieldContext(() => this.id(), 'ds-date');
  protected readonly dialogId = nextId('ds-date-dialog');
  protected readonly formatId = nextId('ds-date-format');
  private readonly formDisabled = signal(false);
  protected readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  protected readonly invalidText = signal(false);
  /** Texto visible: sigue al valor y la persona puede escribir encima. */
  protected readonly text = linkedSignal(() => this.format(this.value()));
  protected readonly describedBy = computed(() =>
    [this.formatId, this.field.describedBy()].filter(Boolean).join(' '),
  );
  protected readonly toggleLabel = computed(() => {
    const date = parseIso(this.value());
    return date ? `Cambiar fecha, ${toLong(date)}` : 'Elegir fecha';
  });
  private readonly host: HTMLElement = inject(ElementRef).nativeElement;
  private readonly anchor = viewChild.required<ElementRef<HTMLElement>>('anchor');
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');
  private readonly toggleButton = viewChild.required<ElementRef<HTMLButtonElement>>('toggleButton');
  private readonly dialog = viewChild<ElementRef<HTMLElement>>('dialog');
  private readonly calendar = viewChild(DsCalendar);
  protected readonly panel = floatingPanel(
    () => this.anchor().nativeElement,
    () => this.dialog()?.nativeElement,
  );
  private readonly cdr = inject(ChangeDetectorRef);
  private onChange: (value: string | null) => void = () => {};
  private onTouched: () => void = () => {};
  private onValidatorChange: () => void = () => {};

  focus(): void {
    this.input().nativeElement.focus();
  }

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLInputElement).value);
  }

  protected onInputKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') this.commitText();
    if (event.key === 'ArrowDown' && event.altKey) {
      event.preventDefault();
      this.open();
    }
  }

  /** Interpreta lo escrito. Un texto inválido se conserva para poder corregirlo. */
  protected commitText(): void {
    const raw = this.text();
    const date = raw.trim() ? parseDisplay(raw) : null;
    this.invalidText.set(!!raw.trim() && !date);
    const iso = date ? toIso(date) : null;
    if (iso !== this.value()) this.commit(iso);
    this.text.set(date ? toDisplay(date) : raw);
    this.onValidatorChange();
    this.onTouched();
  }

  protected toggle(): void {
    if (this.panel.open()) this.close(true);
    else this.open();
  }

  protected pick(iso: string | null): void {
    this.invalidText.set(false);
    this.commit(iso);
    this.text.set(this.format(iso));
    this.onValidatorChange();
    this.close(true);
  }

  protected onEscape(event: Event): void {
    event.preventDefault();
    this.close(true);
  }

  /** Cierra si el foco sale del control con Tab; los clics fuera los atiende el panel. */
  protected onFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget as Node | null;
    if (this.panel.open() && next && !this.host.contains(next)) this.close(false);
  }

  private open(): void {
    if (this.isDisabled() || this.panel.open()) return;
    this.panel.show();
    this.cdr.detectChanges();
    this.panel.place();
    // El panel debe estar visible antes de recibir el foco: el navegador no enfoca lo oculto.
    this.cdr.detectChanges();
    this.calendar()?.focusDay();
  }

  private close(returnFocus: boolean): void {
    this.panel.hide();
    if (returnFocus) this.toggleButton().nativeElement.focus();
  }

  private format(iso: string | null): string {
    const date = parseIso(iso);
    return date ? toDisplay(date) : '';
  }

  private commit(value: string | null): void {
    this.value.set(value);
    this.onChange(value);
  }

  validate(): ValidationErrors | null {
    if (this.invalidText()) return { dsDate: true };
    const value = this.value();
    const min = this.min();
    const max = this.max();
    if (value && min && value < min) return { dsDateMin: { min } };
    if (value && max && value > max) return { dsDateMax: { max } };
    return null;
  }
  registerOnValidatorChange(fn: () => void): void {
    this.onValidatorChange = fn;
  }
  writeValue(value: unknown): void {
    const iso = typeof value === 'string' && parseIso(value) ? value : null;
    this.value.set(iso);
    this.text.set(this.format(iso));
    this.invalidText.set(false);
  }
  registerOnChange(fn: (value: string | null) => void): void {
    this.onChange = fn;
  }
  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }
  setDisabledState(disabled: boolean): void {
    this.formDisabled.set(disabled);
    if (disabled) this.close(false);
  }
}
