import { booleanAttribute, Component, Directive, computed, inject, input } from '@angular/core';

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
    '[class.ds-button--busy]': 'busy()',
  },
})
export class DsButton {
  readonly variant = input<'primary' | 'secondary' | 'quiet' | 'danger'>('primary');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly busy = input(false, { transform: booleanAttribute });
  readonly full = input(false, { transform: booleanAttribute });
}

@Directive({ selector: 'a[dsLink]', host: { class: 'ds-link' } })
export class DsLink {}

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

@Component({
  selector: 'ds-alert',
  host: {
    class: 'ds-alert',
    '[attr.data-tone]': 'tone()',
    '[attr.role]': 'urgent() ? "alert" : "status"',
  },
  template: '<strong>{{ heading() }}</strong><div><ng-content /></div>',
})
export class DsAlert {
  readonly tone = input<DsTone>('info');
  readonly heading = input.required<string>();
  readonly urgent = input(false, { transform: booleanAttribute });
}

let fieldSequence = 0;
@Component({
  selector: 'ds-field',
  host: { class: 'ds-field' },
  template: `
    <label [for]="controlId()"
      >{{ label() }}
      @if (required()) {
        <span aria-hidden="true">*</span>
      }
    </label>
    <ng-content />
    @if (hint()) {
      <p class="ds-field__hint" [id]="controlId() + '-hint'">{{ hint() }}</p>
    }
    @if (error()) {
      <p class="ds-field__error" [id]="controlId() + '-error'" role="alert">{{ error() }}</p>
    }
  `,
})
export class DsField {
  readonly controlId = input('ds-field-' + ++fieldSequence);
  readonly label = input.required<string>();
  readonly hint = input('');
  readonly error = input('');
  readonly required = input(false, { transform: booleanAttribute });
}

@Directive({
  selector: 'input[dsInput], textarea[dsInput], select[dsSelect]',
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

@Component({
  selector: 'ds-skeleton',
  host: { class: 'ds-skeleton', 'aria-hidden': 'true' },
  template: '',
})
export class DsSkeleton {}

@Component({
  selector: 'ds-empty-state',
  host: { class: 'ds-empty' },
  template: `
    <div class="ds-empty__symbol" aria-hidden="true">◇</div>
    <h3>{{ heading() }}</h3>
    <p class="ds-muted">{{ description() }}</p>
    <ng-content />
  `,
})
export class DsEmptyState {
  readonly heading = input.required<string>();
  readonly description = input.required<string>();
}

@Component({
  selector: 'ds-stat',
  host: { class: 'ds-stat' },
  template:
    '<p class="ds-caption">{{ label() }}</p><p class="ds-stat__value">{{ value() }}</p><p class="ds-caption">{{ detail() }}</p>',
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
    '<img src="doggycredit.svg" alt="" width="48" height="48" /><span>Doggy<span class="ds-brand__accent">Credit</span></span>',
})
export class DsBrand {}
