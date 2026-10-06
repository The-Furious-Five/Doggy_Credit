import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  DsAlert,
  DsBadge,
  DsBrand,
  DsButton,
  DsCalendar,
  DsCard,
  DsCheckbox,
  DsCombobox,
  DsDatePicker,
  DsDialog,
  DsEmptyState,
  DsField,
  DsIcon,
  DsInput,
  DsLink,
  DsLogo,
  DsMenu,
  DsMenuItem,
  DsNumberField,
  DsOption,
  DsPagination,
  DsProgress,
  DsRadio,
  DsSelect,
  DsSkeleton,
  DsSpinner,
  DsStat,
  DsSteps,
  DsSwatch,
  DsSwitch,
  DsTab,
  DsTable,
  DsTabs,
  DsThemeService,
  DsThemeToggle,
  DsMarquee,
  DsToastService,
} from '../design-system';

const now = new Date();
const pad = (n: number) => String(n).padStart(2, '0');

@Component({
  selector: 'dc-design-system',
  imports: [
    DsThemeToggle,
    DsMarquee,
    FormsModule,
    RouterLink,
    DsAlert,
    DsBadge,
    DsBrand,
    DsButton,
    DsCalendar,
    DsCard,
    DsCheckbox,
    DsCombobox,
    DsDatePicker,
    DsDialog,
    DsEmptyState,
    DsField,
    DsIcon,
    DsInput,
    DsLink,
    DsLogo,
    DsMenu,
    DsNumberField,
    DsPagination,
    DsProgress,
    DsRadio,
    DsSelect,
    DsSkeleton,
    DsSpinner,
    DsStat,
    DsSteps,
    DsSwatch,
    DsSwitch,
    DsTab,
    DsTable,
    DsTabs,
  ],
  templateUrl: './catalog.html',
  styleUrl: './catalog.scss',
})
export class DesignSystemCatalog implements OnDestroy {
  readonly theme = inject(DsThemeService);
  readonly toast = inject(DsToastService);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  private readonly holderControl = viewChild.required<DsCombobox>('holderControl');
  private readonly consentControl =
    viewChild.required<ElementRef<HTMLInputElement>>('consentControl');

  readonly today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  readonly holders: DsOption<string>[] = [
    { value: 'justina-choque', label: 'Justina Choque', description: 'Persona' },
    { value: 'talleres-mamani', label: 'Talleres Mamani SRL', description: 'Pyme' },
    { value: 'justina-apaza', label: 'Justina Apaza Colque', description: 'Persona' },
    { value: 'integracion', label: 'Cooperativa Integración', description: 'Institución' },
  ];
  readonly purposes: DsOption<string>[] = [
    { value: 'inicial', label: 'Evaluación inicial' },
    { value: 'revision', label: 'Revisión de crédito' },
    {
      value: 'renovacion',
      label: 'Renovación',
      description: 'Requiere una evaluación previa',
      disabled: true,
    },
  ];
  readonly sources: DsOption<string>[] = [
    { value: 'BURO', label: 'Buró de crédito' },
    { value: 'BANCO', label: 'Banco' },
    { value: 'WALLET', label: 'Billetera móvil' },
  ];
  readonly steps = ['Titular', 'Consentimiento', 'Evaluación', 'Resultado'];
  readonly menuItems: DsMenuItem[] = [
    { id: 'detail', label: 'Ver detalle', icon: 'search' },
    { id: 'repeat', label: 'Repetir evaluación', icon: 'arrow' },
    { id: 'revoke', label: 'Revocar consentimiento', icon: 'close', tone: 'danger' },
  ];
  readonly moreItems: DsMenuItem[] = [
    { id: 'copy', label: 'Copiar identificador' },
    { id: 'archive', label: 'Archivar', disabled: true },
  ];

  holder: string | null = null;
  purpose = 'inicial';
  selectedSources = ['BURO'];
  requestDate: string | null = this.today;
  amount: number | null = 15000;
  term: number | null = 12;
  notify = true;
  consent = false;
  calendarDate: string | null = null;
  tab = 'summary';
  page = 1;
  private timer?: ReturnType<typeof setTimeout>;

  readonly example = `import { DsField, DsSelect } from '../design-system';

<ds-field label="Finalidad" required>
  <ds-select [options]="purposes" [(ngModel)]="purpose" />
</ds-field>
<button dsButton type="submit" [busy]="saving()">Guardar</button>`;

  save(): void {
    this.submitted.set(true);
    if (this.busy()) return;
    if (!this.holder) {
      this.holderControl().focus();
      return;
    }
    if (!this.consent) {
      this.consentControl().nativeElement.focus();
      return;
    }
    this.busy.set(true);
    this.timer = setTimeout(() => {
      this.busy.set(false);
      this.toast.show('Ejemplo guardado. No se enviaron datos.');
    }, 700);
  }
  reset(): void {
    this.holder = null;
    this.consent = false;
    this.submitted.set(false);
    this.toast.show('Ejemplo restablecido.');
  }
  onMenu(id: string): void {
    const item = [...this.menuItems, ...this.moreItems].find((entry) => entry.id === id);
    this.toast.show(`Acción elegida: ${item?.label ?? id}.`);
  }
  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }
}
