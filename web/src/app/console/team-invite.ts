import { HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { MiembroEquipo, MiembroEquipoNuevoRolEnum, TenantService } from '../api';
import { HANDLED_PROBLEMS } from '../http/api-error.interceptor';
import {
  DsAlert,
  DsButton,
  DsDialog,
  DsField,
  DsIcon,
  DsInput,
  DsOption,
  DsSelect,
  DsToastService,
} from '../design-system';

type Problem = 'limit' | 'exists' | 'unavailable' | null;

/**
 * A-10 (LOS-71): invitación al equipo. El límite de usuarios lo decide el backend
 * (PLAN_LIMIT_REACHED); la vista solo explica el error que recibe.
 */
@Component({
  selector: 'dc-team-invite',
  imports: [FormsModule, DsAlert, DsButton, DsDialog, DsField, DsIcon, DsInput, DsSelect],
  template: `
    <button dsButton (click)="open()"><ds-icon name="plus" /> Invitar</button>
    <ds-dialog heading="Invitar a tu equipo" (dismissed)="reset()">
      <form class="ds-stack" (ngSubmit)="invite()" novalidate>
        <p class="ds-muted">
          La persona define su contraseña en su primer ingreso. Un administrador, además, configura la
          verificación en dos pasos.
        </p>
        @switch (problem()) {
          @case ('limit') {
            <ds-alert tone="warning" heading="Tu plan no admite más usuarios" urgent>
              {{ detail() }} Desactiva una cuenta o compara los planes para sumar a más personas.
            </ds-alert>
          }
          @case ('exists') {
            <ds-alert tone="warning" heading="Esa persona ya tiene una cuenta" urgent>
              Revisa el email o búscala en la tabla de usuarios.
            </ds-alert>
          }
          @case ('unavailable') {
            <ds-alert tone="danger" heading="No pudimos enviar la invitación" urgent>
              Los datos se conservan. Revisa tu conexión e inténtalo de nuevo.
            </ds-alert>
          }
        }
        <ds-field label="Nombre completo" required [error]="nameError()">
          <input
            dsInput
            name="memberName"
            autocomplete="name"
            maxlength="100"
            [ngModel]="name"
            (ngModelChange)="name = $event; nameError.set('')"
            [disabled]="busy()"
          />
        </ds-field>
        <ds-field label="Email" required [error]="emailError()">
          <input
            dsInput
            type="email"
            name="memberEmail"
            autocomplete="email"
            maxlength="254"
            [ngModel]="email"
            (ngModelChange)="email = $event; emailError.set('')"
            [disabled]="busy()"
          />
        </ds-field>
        <ds-field label="Rol" hint="Un analista no ve documentos ni montos si la institución los oculta.">
          <ds-select name="memberRole" [options]="roles" [(ngModel)]="role" [disabled]="busy()" />
        </ds-field>
        <div class="ds-actions">
          <button dsButton variant="secondary" [disabled]="busy()" (click)="dialog().close()">Cancelar</button>
          <button dsButton type="submit" [busy]="busy()">Enviar invitación</button>
        </div>
      </form>
    </ds-dialog>
  `,
})
export class TeamInvite {
  readonly invited = output<MiembroEquipo>();
  readonly dialog = viewChild.required(DsDialog);
  private readonly api = inject(TenantService);
  private readonly toast = inject(DsToastService);
  readonly roles: DsOption<MiembroEquipoNuevoRolEnum>[] = [
    { value: MiembroEquipoNuevoRolEnum.Analyst, label: 'Analista' },
    { value: MiembroEquipoNuevoRolEnum.TenantAdmin, label: 'Administrador de la institución' },
  ];
  readonly busy = signal(false);
  readonly problem = signal<Problem>(null);
  readonly detail = signal('');
  readonly nameError = signal('');
  readonly emailError = signal('');
  name = '';
  email = '';
  role: MiembroEquipoNuevoRolEnum = MiembroEquipoNuevoRolEnum.Analyst;

  open(): void {
    this.dialog().show();
  }

  reset(): void {
    this.name = '';
    this.email = '';
    this.role = MiembroEquipoNuevoRolEnum.Analyst;
    this.busy.set(false);
    this.problem.set(null);
    this.nameError.set('');
    this.emailError.set('');
  }

  async invite(): Promise<void> {
    const name = this.name.trim();
    const email = this.email.trim();
    this.nameError.set(name.length < 2 ? 'Ingresa el nombre y apellido de la persona.' : '');
    this.emailError.set(/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) ? '' : 'Ingresa un email válido, como nombre@institucion.bo.');
    if (this.nameError() || this.emailError()) return;
    this.busy.set(true);
    this.problem.set(null);
    try {
      const context = new HttpContext().set(HANDLED_PROBLEMS, ['PLAN_LIMIT_REACHED', 'MEMBER_ALREADY_EXISTS']);
      const member = await firstValueFrom(
        this.api.invitarMiembroEquipo({ nombre: name, email, rol: this.role }, 'body', false, { context }),
      );
      this.invited.emit(member);
      this.dialog().close();
      this.toast.show(`Invitación creada para ${member.email ?? email}.`);
    } catch (error) {
      const code = error instanceof HttpErrorResponse ? error.error?.code : undefined;
      this.detail.set(error instanceof HttpErrorResponse ? (error.error?.detail ?? '') : '');
      this.problem.set(
        code === 'PLAN_LIMIT_REACHED' ? 'limit' : code === 'MEMBER_ALREADY_EXISTS' ? 'exists' : 'unavailable',
      );
    } finally {
      this.busy.set(false);
    }
  }
}
