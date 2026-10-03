import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import {
  DsAlert,
  DsButton,
  DsCheckbox,
  DsDialog,
  DsField,
  DsInput,
  DsLink,
  DsLogo,
  DsRadio,
  DsToastService,
} from './index';

@Component({
  imports: [FormsModule, DsButton, DsCheckbox, DsDialog, DsField, DsInput, DsRadio],
  template: `
    <button dsButton [busy]="busy()" (click)="count = count + 1">Guardar</button>
    <ds-field label="Nombre" hint="Solo datos ficticios" [error]="error()" required>
      <input dsInput name="name" [(ngModel)]="name" />
    </ds-field>
    <label><input dsCheckbox type="checkbox" [(ngModel)]="checked" />Confirmar</label>
    <label
      ><input dsRadio type="radio" name="theme" value="light" [(ngModel)]="theme" />Claro</label
    >
    <label
      ><input dsRadio type="radio" name="theme" value="dark" [(ngModel)]="theme" />Oscuro</label
    >
    <ds-dialog heading="Confirmación"><p>Revisa el cambio.</p></ds-dialog>
  `,
})
class Fixture {
  busy = signal(false);
  error = signal('');
  count = 0;
  name = '';
  checked = false;
  theme = 'light';
}

describe('Contrato accesible del Design System', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [Fixture] }));
  it('bloquea acciones repetidas durante la carga y restaura el botón', () => {
    const fixture = TestBed.createComponent(Fixture);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.type).toBe('button');
    fixture.componentInstance.busy.set(true);
    fixture.detectChanges();
    button.click();
    expect(fixture.componentInstance.count).toBe(0);
    expect(button.getAttribute('aria-busy')).toBe('true');
    fixture.componentInstance.busy.set(false);
    fixture.detectChanges();
    button.click();
    expect(fixture.componentInstance.count).toBe(1);
  });
  it('conecta etiqueta, ayuda y error al control sin que cada pantalla lo repita', () => {
    const fixture = TestBed.createComponent(Fixture);
    fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[dsInput]');
    const label: HTMLLabelElement = fixture.nativeElement.querySelector('ds-field label');
    expect(label.htmlFor).toBe(input.id);
    expect(input.required).toBe(true);
    expect(input.getAttribute('aria-describedby')).toBe(input.id + '-hint');
    fixture.componentInstance.error.set('Escribe un nombre.');
    fixture.detectChanges();
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe(`${input.id}-hint ${input.id}-error`);
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Escribe un nombre.',
    );
  });
  it('conserva el funcionamiento nativo de checkbox/radio con Angular Forms', async () => {
    const fixture = TestBed.createComponent(Fixture);
    fixture.detectChanges();
    await fixture.whenStable();
    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('[dsCheckbox]');
    checkbox.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.checked).toBe(true);
    const radio: HTMLInputElement = fixture.nativeElement.querySelector('[value="dark"]');
    radio.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.theme).toBe('dark');
  });
  it('el diálogo tiene nombre accesible y usa la semántica modal nativa', () => {
    const fixture = TestBed.createComponent(Fixture);
    fixture.detectChanges();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    const id = dialog.getAttribute('aria-labelledby');
    expect(fixture.nativeElement.querySelector('#' + id).textContent).toBe('Confirmación');
    expect(dialog.querySelector('button')?.getAttribute('aria-label')).toBe('Cerrar diálogo');
  });
});

@Component({
  imports: [DsAlert, DsButton, DsLink, DsLogo],
  template: `
    <ds-alert [tone]="tone()" heading="Aviso" [urgent]="urgent()">Detalle</ds-alert>
    <button dsButton icon variant="quiet" aria-label="Buscar">·</button>
    <ds-logo />
    <ds-logo label="DoggyCredit" />
    <a dsLink href="/planes">Ver planes</a>
    <a dsLink appearance="primary" href="/acceso">Empezar</a>
  `,
})
class SignalsFixture {
  tone = signal<'info' | 'danger'>('info');
  urgent = signal(false);
}

describe('Señales visuales con significado accesible', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [SignalsFixture] }));
  it('la alerta acompaña el tono con un icono decorativo y solo interrumpe si es urgente', () => {
    const fixture = TestBed.createComponent(SignalsFixture);
    fixture.detectChanges();
    const alert: HTMLElement = fixture.nativeElement.querySelector('ds-alert');
    const icon = () => alert.querySelector('ds-icon path')!.getAttribute('d');
    expect(alert.getAttribute('role')).toBe('status');
    expect(alert.querySelector('ds-icon')!.getAttribute('aria-hidden')).toBe('true');
    const infoIcon = icon();
    fixture.componentInstance.tone.set('danger');
    fixture.componentInstance.urgent.set(true);
    fixture.detectChanges();
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.getAttribute('data-tone')).toBe('danger');
    expect(icon()).not.toBe(infoIcon);
    expect(alert.textContent).toContain('Aviso');
  });
  it('el botón de icono conserva su nombre accesible', () => {
    const fixture = TestBed.createComponent(SignalsFixture);
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.classList).toContain('ds-button--icon');
    expect(button.getAttribute('aria-label')).toBe('Buscar');
  });
  it('un enlace puede presentarse como botón sin dejar de ser navegación', () => {
    const fixture = TestBed.createComponent(SignalsFixture);
    fixture.detectChanges();
    const [link, cta] = fixture.nativeElement.querySelectorAll('a');
    expect(link.classList).toContain('ds-link');
    expect(link.classList).not.toContain('ds-button');
    expect(cta.classList).toContain('ds-button');
    expect(cta.getAttribute('data-variant')).toBe('primary');
    expect(cta.getAttribute('href')).toBe('/acceso');
  });
  it('el logo es decorativo salvo que se le dé un nombre', () => {
    const fixture = TestBed.createComponent(SignalsFixture);
    fixture.detectChanges();
    const [decorative, named] = fixture.nativeElement.querySelectorAll('ds-logo img');
    expect(decorative.getAttribute('alt')).toBe('');
    expect(named.getAttribute('alt')).toBe('DoggyCredit');
  });
});

describe('Notificaciones', () => {
  it('pausa el vencimiento durante la interacción y permite cerrar', () => {
    vi.useFakeTimers();
    const toast = new DsToastService();
    try {
      toast.show('Guardado');
      toast.pause();
      vi.advanceTimersByTime(10000);
      expect(toast.message()).toBe('Guardado');
      toast.resume();
      vi.advanceTimersByTime(8000);
      expect(toast.message()).toBe('');
      toast.show('Otro aviso');
      toast.dismiss();
      expect(toast.message()).toBe('');
    } finally {
      toast.ngOnDestroy();
      vi.useRealTimers();
    }
  });
});
