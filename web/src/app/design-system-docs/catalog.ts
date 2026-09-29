import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  DsAlert,
  DsBadge,
  DsBrand,
  DsButton,
  DsCard,
  DsCheckbox,
  DsDialog,
  DsEmptyState,
  DsField,
  DsIcon,
  DsInput,
  DsLink,
  DsRadio,
  DsSkeleton,
  DsSpinner,
  DsStat,
  DsTable,
  DsThemeService,
  DsToastService,
} from '../design-system';
@Component({
  selector: 'dc-design-system',
  imports: [
    FormsModule,
    DsAlert,
    DsBadge,
    DsBrand,
    DsButton,
    DsCard,
    DsCheckbox,
    DsDialog,
    DsEmptyState,
    DsField,
    DsIcon,
    DsInput,
    DsLink,
    DsRadio,
    DsSkeleton,
    DsSpinner,
    DsStat,
    DsTable,
  ],
  templateUrl: './catalog.html',
  styleUrl: './catalog.scss',
})
export class DesignSystemCatalog implements OnDestroy {
  readonly theme = inject(DsThemeService);
  readonly toast = inject(DsToastService);
  readonly busy = signal(false);
  readonly submitted = signal(false);
  private readonly nameControl = viewChild.required<ElementRef<HTMLInputElement>>('nameControl');
  private readonly consentControl =
    viewChild.required<ElementRef<HTMLInputElement>>('consentControl');
  name = '';
  purpose = 'Evaluación inicial';
  consent = false;
  private timer?: ReturnType<typeof setTimeout>;
  readonly example = `<ds-field label="Nombre" hint="Solo datos ficticios" required>
  <input dsInput [(ngModel)]="name" />
</ds-field>
<button dsButton [busy]="saving()">Guardar</button>`;
  save(): void {
    this.submitted.set(true);
    if (this.busy()) return;
    if (!this.name.trim()) {
      this.nameControl().nativeElement.focus();
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
    this.name = '';
    this.consent = false;
    this.submitted.set(false);
    this.toast.show('Ejemplo restablecido.');
  }
  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }
}
