import { DOCUMENT } from '@angular/common';
import { booleanAttribute, Component, Directive, computed, inject, input } from '@angular/core';
import { DsIcon, DsIconName } from './icon';

export type DsTone = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

@Directive({
  selector: 'button[dsButton]',
  host: {
    class: 'ds-button',
    '[attr.data-variant]': 'variant()',
    '[attr.type]': 'type()',
    '[disabled]': 'disabled() || busy()',
    '[attr.aria-busy]': 'busy() || null',
    '[class.ds-button--full]': 'full()',
    '[class.ds-button--icon]': 'icon()',
    '[class.ds-button--busy]': 'busy()',
  },
})
export class DsButton {
  readonly variant = input<'primary' | 'secondary' | 'quiet' | 'danger'>('primary');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly busy = input(false, { transform: booleanAttribute });
  readonly full = input(false, { transform: booleanAttribute });
  /** Botón cuadrado solo con icono. Requiere aria-label. */
  readonly icon = input(false, { transform: booleanAttribute });
}

/**
 * Navegación real (href/routerLink). `appearance` la presenta como botón para
 * llamadas a la acción que llevan a otra página; sigue siendo un enlace.
 */
@Directive({
  selector: 'a[dsLink]',
  host: {
    '[class.ds-link]': 'appearance() === "link"',
    '[class.ds-button]': 'appearance() !== "link"',
    '[attr.data-variant]': 'appearance() === "link" ? null : appearance()',
  },
})
export class DsLink {
  readonly appearance = input<'link' | 'primary' | 'secondary' | 'quiet'>('link');
}

@Component({
  selector: 'ds-card',
  host: { class: 'ds-card', '[attr.data-padding]': 'padding()' },
  template: '<ng-content />',
})
export class DsCard {
  readonly padding = input<'normal' | 'compact'>('normal');
}

@Component({
  selector: 'ds-badge',
  host: { class: 'ds-badge', '[attr.data-tone]': 'tone()' },
  template: '<ng-content />',
})
export class DsBadge {
  readonly tone = input<DsTone>('neutral');
}

const toneIcons: Record<DsTone, DsIconName> = {
  neutral: 'info',
  info: 'info',
  success: 'success',
  warning: 'warning',
  danger: 'danger',
};

@Component({
  selector: 'ds-alert',
  imports: [DsIcon],
  host: {
    class: 'ds-alert',
    '[attr.data-tone]': 'tone()',
    '[attr.role]': 'urgent() ? "alert" : "status"',
  },
  template: `
    <ds-icon class="ds-alert__icon" [name]="icon()" />
    <div>
      <strong class="ds-alert__heading">{{ heading() }}</strong>
      <div class="ds-alert__content"><ng-content /></div>
    </div>
  `,
})
export class DsAlert {
  readonly tone = input<DsTone>('info');
  readonly heading = input.required<string>();
  readonly urgent = input(false, { transform: booleanAttribute });
  readonly icon = computed(() => toneIcons[this.tone()]);
}

let fieldSequence = 0;
@Component({
  selector: 'ds-field',
  imports: [DsIcon],
  host: { class: 'ds-field' },
  template: `
    <label [id]="controlId() + '-label'" [for]="controlId()" (click)="focusControl()"
      >{{ label() }}
      @if (required()) {
        <span class="ds-field__required" aria-hidden="true">*</span>
      }
    </label>
    <ng-content />
    @if (hint()) {
      <p class="ds-field__hint" [id]="controlId() + '-hint'">{{ hint() }}</p>
    }
    @if (error()) {
      <p class="ds-field__error" [id]="controlId() + '-error'" role="alert">
        <ds-icon name="danger" />{{ error() }}
      </p>
    }
  `,
})
export class DsField {
  readonly controlId = input('ds-field-' + ++fieldSequence);
  readonly label = input.required<string>();
  readonly hint = input('');
  readonly error = input('');
  readonly required = input(false, { transform: booleanAttribute });
  private readonly document = inject(DOCUMENT);

  /** Los controles nativos reciben el foco por `for`; los propios (role=combobox) no son etiquetables. */
  focusControl(): void {
    const control = this.document.getElementById(this.controlId());
    if (control && !('labels' in control)) control.focus();
  }
}

@Directive({
  selector: 'input[dsInput], textarea[dsInput]',
  host: {
    class: 'ds-control',
    '[id]': 'controlId()',
    '[attr.aria-describedby]': 'description()',
    '[attr.aria-invalid]': 'field?.error() ? "true" : null',
    '[required]': 'required() || field?.required() || false',
  },
})
export class DsInput {
  readonly field = inject(DsField, { optional: true });
  readonly id = input('');
  readonly describedBy = input('', { alias: 'aria-describedby' });
  readonly required = input(false, { transform: booleanAttribute });
  readonly controlId = computed(() => this.field?.controlId() || this.id());
  readonly description = computed(
    () =>
      [
        this.describedBy(),
        this.field?.hint() ? this.controlId() + '-hint' : '',
        this.field?.error() ? this.controlId() + '-error' : '',
      ]
        .filter(Boolean)
        .join(' ') || null,
  );
}

@Directive({ selector: 'input[dsCheckbox]', host: { class: 'ds-checkbox', type: 'checkbox' } })
export class DsCheckbox {}
@Directive({ selector: 'input[dsRadio]', host: { class: 'ds-radio', type: 'radio' } })
export class DsRadio {}
@Directive({ selector: 'table[dsTable]', host: { class: 'ds-table' } })
export class DsTable {}

@Component({
  selector: 'ds-spinner',
  host: { class: 'ds-loading', role: 'status' },
  template: '<span class="ds-spinner" aria-hidden="true"></span><span>{{ label() }}</span>',
})
export class DsSpinner {
  readonly label = input('Cargando…');
}

let progressSequence = 0;
@Component({
  selector: 'ds-progress',
  host: { class: 'ds-progress' },
  template: `
    <div class="ds-progress__header">
      <span [id]="labelId">{{ label() }}</span>
      @if (percent() !== null) {
        <span class="ds-progress__value" aria-hidden="true">{{ percent() }} %</span>
      }
    </div>
    <div
      class="ds-progress__track"
      role="progressbar"
      [attr.aria-labelledby]="labelId"
      [attr.aria-valuemin]="percent() === null ? null : 0"
      [attr.aria-valuemax]="percent() === null ? null : 100"
      [attr.aria-valuenow]="percent()"
    >
      <span
        class="ds-progress__bar"
        [class.ds-progress__bar--indeterminate]="percent() === null"
        [style.width.%]="percent()"
      ></span>
    </div>
  `,
})
export class DsProgress {
  readonly label = input.required<string>();
  /** `null` indica progreso indeterminado. */
  readonly value = input<number | null>(null);
  readonly max = input(100);
  readonly labelId = 'ds-progress-' + ++progressSequence;
  readonly percent = computed(() => {
    const value = this.value();
    if (value === null) return null;
    return Math.round(Math.min(100, Math.max(0, (value / (this.max() || 1)) * 100)));
  });
}

@Component({
  selector: 'ds-skeleton',
  host: { class: 'ds-skeleton', 'aria-hidden': 'true' },
  template: '',
})
export class DsSkeleton {}

@Component({
  selector: 'ds-empty-state',
  imports: [DsIcon],
  host: { class: 'ds-empty' },
  template: `
    <span class="ds-empty__symbol" aria-hidden="true"><ds-icon [name]="icon()" /></span>
    <h3>{{ heading() }}</h3>
    <p class="ds-muted">{{ description() }}</p>
    <ng-content />
  `,
})
export class DsEmptyState {
  readonly heading = input.required<string>();
  readonly description = input.required<string>();
  readonly icon = input<DsIconName>('tray');
}

@Component({
  selector: 'ds-stat',
  host: { class: 'ds-stat' },
  template: `
    <p class="ds-stat__label">{{ label() }}</p>
    <p class="ds-stat__value">{{ value() }}</p>
    @if (detail()) {
      <p class="ds-stat__detail">{{ detail() }}</p>
    }
  `,
})
export class DsStat {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly detail = input('');
}

@Component({
  selector: 'ds-brand',
  host: { class: 'ds-brand' },
  template:
    '<img class="ds-brand__mark" src="doggycredit-mark.svg" alt="" width="36" height="36" /><span>Doggy<span class="ds-brand__accent">Credit</span></span>',
})
export class DsBrand {}

export type DsColorToken =
  | 'brand-pine'
  | 'brand-forest'
  | 'brand-sage'
  | 'brand-honey'
  | 'brand-sand'
  | 'brand-ivory'
  | 'brand-cocoa'
  | 'primary'
  | 'link'
  | 'accent'
  | 'text'
  | 'muted'
  | 'bg'
  | 'surface'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info';

/** Muestra decorativa de un token de color. Acompañar siempre con su nombre en texto. */
@Component({
  selector: 'ds-swatch',
  host: {
    class: 'ds-swatch',
    'aria-hidden': 'true',
    '[attr.data-shape]': 'shape()',
    '[style.background]': 'fill()',
  },
  template: '',
})
export class DsSwatch {
  readonly color = input.required<DsColorToken>();
  readonly shape = input<'tile' | 'dot'>('tile');
  readonly fill = computed(() => `var(--ds-${this.color()})`);
}

/** Emblema del logo sobre su placa marfil, como un icono de aplicación. */
@Component({
  selector: 'ds-logo',
  host: { class: 'ds-logo' },
  template: '<img src="doggycredit-mark.svg" [alt]="label()" width="72" height="72" />',
})
export class DsLogo {
  /** Vacío cuando el nombre de la marca ya aparece junto al logo. */
  readonly label = input('');
}
