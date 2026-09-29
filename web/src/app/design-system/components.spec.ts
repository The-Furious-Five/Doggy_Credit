import { Component, Type, signal, viewChild } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, NgModel } from '@angular/forms';
import {
  DsCalendar,
  DsCombobox,
  DsDatePicker,
  DsField,
  DsMenu,
  DsMenuItem,
  DsNumberField,
  DsOption,
  DsPagination,
  DsProgress,
  DsSelect,
  DsSteps,
  DsSwitch,
  DsTab,
  DsTabs,
} from './index';
import { placePanel } from './internal';

const key = (target: Element, name: string, init: KeyboardEventInit = {}) =>
  target.dispatchEvent(
    new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true, ...init }),
  );
const type = (input: HTMLInputElement, text: string) => {
  input.value = text;
  input.dispatchEvent(new Event('input'));
};
const active = () => document.activeElement as HTMLElement;

async function render<T>(component: Type<T>): Promise<ComponentFixture<T>> {
  const fixture = TestBed.createComponent(component);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture;
}

@Component({
  imports: [
    FormsModule,
    DsField,
    DsSelect,
    DsCombobox,
    DsDatePicker,
    DsNumberField,
    DsSwitch,
  ],
  template: `
    <ds-field label="Finalidad" [error]="error()">
      <ds-select name="purpose" [options]="options" [(ngModel)]="purpose" />
    </ds-field>
    <ds-field label="Fuentes">
      <ds-select name="sources" multiple [options]="options" [(ngModel)]="sources" />
    </ds-field>
    <ds-field label="Titular">
      <ds-combobox name="holder" [options]="holders" [(ngModel)]="holder" />
    </ds-field>
    <ds-field label="Fecha">
      <ds-date-picker
        name="date"
        min="2026-09-01"
        max="2026-12-31"
        [(ngModel)]="date"
        #dateModel="ngModel"
      />
    </ds-field>
    <ds-field label="Plazo">
      <ds-number-field name="term" suffix="meses" [min]="1" [max]="60" [(ngModel)]="term" />
    </ds-field>
    <ds-switch name="notify" [(ngModel)]="notify">Avisar al titular</ds-switch>
  `,
})
class FormFixture {
  readonly error = signal('');
  readonly dateModel = viewChild.required<NgModel>('dateModel');
  readonly options: DsOption<string>[] = [
    { value: 'inicial', label: 'Evaluación inicial' },
    { value: 'revision', label: 'Revisión de crédito', disabled: true },
    { value: 'renovacion', label: 'Renovación' },
  ];
  readonly holders: DsOption<string>[] = [
    { value: '1', label: 'Justina Choque' },
    { value: '2', label: 'Talleres Mamani SRL' },
    { value: '3', label: 'Justina Apaza Colque' },
    { value: '4', label: 'Cooperativa Integración' },
  ];
  purpose: string | null = null;
  sources: string[] = [];
  holder: string | null = null;
  date: string | null = '2026-09-29';
  term: number | null = 12;
  notify = false;
}

describe('ds-select', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [FormFixture] }));

  it('usa el nombre de su etiqueta, abre con teclado, salta opciones deshabilitadas y elige con Enter', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const combo = root.querySelector<HTMLElement>('ds-select [role="combobox"]')!;
    expect(combo.getAttribute('aria-labelledby')).toBe(root.querySelector('ds-field label')!.id);
    expect(combo.getAttribute('aria-expanded')).toBe('false');
    expect(combo.textContent).toContain('Selecciona una opción');

    combo.focus();
    key(combo, 'ArrowDown');
    fixture.detectChanges();
    const options = root.querySelectorAll<HTMLElement>('[role="option"]');
    expect(combo.getAttribute('aria-expanded')).toBe('true');
    expect(combo.getAttribute('aria-controls')).toBe(root.querySelector('[role="listbox"]')!.id);
    expect(combo.getAttribute('aria-activedescendant')).toBe(options[0].id);

    key(combo, 'ArrowDown');
    fixture.detectChanges();
    expect(combo.getAttribute('aria-activedescendant')).toBe(options[2].id);

    key(combo, 'Enter');
    fixture.detectChanges();
    expect(fixture.componentInstance.purpose).toBe('renovacion');
    expect(combo.getAttribute('aria-expanded')).toBe('false');
    expect(combo.textContent).toContain('Renovación');
    expect(active()).toBe(combo);
  });

  it('escritura anticipada, Escape sin cambiar el valor y cierre al pulsar fuera', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const combo = root.querySelector<HTMLElement>('ds-select [role="combobox"]')!;
    combo.focus();
    key(combo, 'r');
    fixture.detectChanges();
    const options = root.querySelectorAll<HTMLElement>('[role="option"]');
    expect(combo.getAttribute('aria-activedescendant')).toBe(options[2].id);
    key(combo, 'Escape');
    fixture.detectChanges();
    expect(combo.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.componentInstance.purpose).toBeNull();

    combo.click();
    fixture.detectChanges();
    expect(combo.getAttribute('aria-expanded')).toBe('true');
    document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    fixture.detectChanges();
    expect(combo.getAttribute('aria-expanded')).toBe('false');
  });

  it('múltiple: alterna opciones sin cerrarse y respeta las deshabilitadas', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const combo = root.querySelectorAll<HTMLElement>('ds-select [role="combobox"]')[1];
    combo.click();
    fixture.detectChanges();
    expect(root.querySelector('[role="listbox"]')!.getAttribute('aria-multiselectable')).toBe(
      'true',
    );
    const options = root.querySelectorAll<HTMLElement>('[role="option"]');
    options[2].click();
    options[1].click();
    options[0].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.sources).toEqual(['inicial', 'renovacion']);
    expect(options[0].getAttribute('aria-selected')).toBe('true');
    expect(options[1].getAttribute('aria-selected')).toBe('false');
    expect(combo.getAttribute('aria-expanded')).toBe('true');
    expect(combo.textContent).toContain('Evaluación inicial, Renovación');
  });

  it('refleja el error de ds-field y la etiqueta lleva el foco al control', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const combo = root.querySelector<HTMLElement>('ds-select [role="combobox"]')!;
    fixture.componentInstance.error.set('Elige una finalidad.');
    fixture.detectChanges();
    expect(combo.getAttribute('aria-invalid')).toBe('true');
    expect(combo.getAttribute('aria-describedby')).toBe(`${combo.id}-error`);
    root.querySelector<HTMLElement>('ds-field label')!.click();
    expect(active()).toBe(combo);
  });
});

describe('ds-combobox', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [FormFixture] }));

  it('filtra sin distinguir tildes, elige con flechas y descarta texto sin coincidencia al salir', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const input = root.querySelector<HTMLInputElement>('ds-combobox input')!;
    expect(input.getAttribute('role')).toBe('combobox');

    input.focus();
    type(input, 'integracion');
    fixture.detectChanges();
    expect(root.querySelectorAll('ds-combobox [role="option"]').length).toBe(1);

    type(input, 'justina');
    fixture.detectChanges();
    expect(input.getAttribute('aria-expanded')).toBe('true');
    expect(root.querySelectorAll('ds-combobox [role="option"]').length).toBe(2);
    key(input, 'ArrowDown');
    key(input, 'ArrowDown');
    key(input, 'Enter');
    fixture.detectChanges();
    expect(fixture.componentInstance.holder).toBe('3');
    expect(input.value).toBe('Justina Apaza Colque');
    expect(input.getAttribute('aria-expanded')).toBe('false');

    type(input, 'zzz');
    fixture.detectChanges();
    expect(root.querySelector('ds-combobox .ds-listbox__empty')!.textContent).toContain('zzz');
    input.blur();
    fixture.detectChanges();
    expect(input.value).toBe('Justina Apaza Colque');
    expect(fixture.componentInstance.holder).toBe('3');
  });
});

describe('ds-date-picker', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [FormFixture] }));

  it('interpreta dd/mm/aaaa, conserva el texto inválido y valida el rango', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const input = root.querySelector<HTMLInputElement>('ds-date-picker input')!;
    expect(input.value).toBe('29/09/2026');

    input.focus();
    type(input, '5/10/2026');
    input.blur();
    fixture.detectChanges();
    expect(fixture.componentInstance.date).toBe('2026-10-05');
    expect(input.value).toBe('05/10/2026');

    input.focus();
    type(input, '31/02/2026');
    input.blur();
    fixture.detectChanges();
    expect(fixture.componentInstance.date).toBeNull();
    expect(input.value).toBe('31/02/2026');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.componentInstance.dateModel().errors).toEqual({ dsDate: true });

    input.focus();
    type(input, '15/01/2027');
    input.blur();
    fixture.detectChanges();
    expect(fixture.componentInstance.date).toBe('2027-01-15');
    expect(fixture.componentInstance.dateModel().errors).toEqual({
      dsDateMax: { max: '2026-12-31' },
    });
  });

  it('abre un calendario con el foco en la fecha, se navega con teclado y devuelve el foco', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const toggle = root.querySelector<HTMLButtonElement>('ds-date-picker [aria-haspopup="dialog"]')!;
    expect(toggle.getAttribute('aria-label')).toBe(
      'Cambiar fecha, martes, 29 de septiembre de 2026',
    );
    toggle.click();
    // El navegador no enfoca elementos ocultos: el panel ya debe estar colocado (visible).
    expect(root.querySelector('[role="dialog"]')!.classList).toContain('ds-popover--placed');
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(root.querySelector('[role="dialog"]')!.getAttribute('aria-label')).toBe('Elegir fecha');
    expect(active().dataset['date']).toBe('2026-09-29');

    key(active(), 'ArrowRight');
    key(active(), 'PageDown');
    fixture.detectChanges();
    expect(active().dataset['date']).toBe('2026-10-30');
    expect(root.querySelector('.ds-calendar__title')!.textContent).toContain('Octubre de 2026');

    active().click();
    fixture.detectChanges();
    expect(fixture.componentInstance.date).toBe('2026-10-30');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(active()).toBe(toggle);

    toggle.click();
    fixture.detectChanges();
    key(active(), 'Escape');
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(active()).toBe(toggle);
  });
});

describe('ds-number-field y ds-switch', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [FormFixture] }));

  it('el campo numérico sube y baja por pasos, respeta los límites y acepta coma decimal', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const input = root.querySelector<HTMLInputElement>('ds-number-field input')!;
    const [decrease, increase] = root.querySelectorAll<HTMLButtonElement>(
      'ds-number-field button',
    );
    expect(input.getAttribute('role')).toBe('spinbutton');
    expect(input.getAttribute('aria-valuetext')).toBe('12 meses');

    key(input, 'ArrowUp');
    fixture.detectChanges();
    expect(fixture.componentInstance.term).toBe(13);
    key(input, 'End');
    key(input, 'ArrowUp');
    fixture.detectChanges();
    expect(fixture.componentInstance.term).toBe(60);
    expect(increase.disabled).toBe(true);
    decrease.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.term).toBe(59);

    input.focus();
    type(input, '7,4');
    input.blur();
    fixture.detectChanges();
    expect(fixture.componentInstance.term).toBe(7);
    expect(input.value).toBe('7');
  });

  it('el interruptor cambia con el botón o con su etiqueta', async () => {
    const fixture = await render(FormFixture);
    const root: HTMLElement = fixture.nativeElement;
    const control = root.querySelector<HTMLButtonElement>('[role="switch"]')!;
    const label = root.querySelector<HTMLElement>('.ds-switch__label')!;
    expect(control.getAttribute('aria-checked')).toBe('false');
    expect(root.querySelector('#' + control.getAttribute('aria-labelledby'))).toBe(label);
    control.click();
    fixture.detectChanges();
    expect(control.getAttribute('aria-checked')).toBe('true');
    expect(fixture.componentInstance.notify).toBe(true);
    label.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.notify).toBe(false);
    expect(active()).toBe(control);
  });
});

@Component({
  imports: [DsMenu, DsTabs, DsTab, DsPagination, DsSteps, DsProgress, DsCalendar],
  template: `
    <ds-menu label="Acciones" [items]="items" (itemSelected)="picked = $event" />
    <ds-tabs label="Detalle" [(selected)]="tab">
      <ds-tab value="a" label="Resumen">Uno</ds-tab>
      <ds-tab value="b" label="Fuentes">Dos</ds-tab>
      <ds-tab value="c" label="Historial">Tres</ds-tab>
    </ds-tabs>
    <ds-pagination label="Páginas" [total]="42" [pageSize]="10" [(page)]="page" />
    <ds-steps label="Progreso" [steps]="['Titular', 'Consentimiento', 'Evaluación']" [current]="1" />
    <ds-progress label="Consultando" [value]="30" />
    <ds-progress label="Preparando" />
    <ds-calendar [(value)]="day" min="2026-09-10" max="2026-10-20" />
  `,
})
class NavigationFixture {
  readonly items: DsMenuItem[] = [
    { id: 'detail', label: 'Ver detalle' },
    { id: 'archive', label: 'Archivar', disabled: true },
    { id: 'revoke', label: 'Revocar', tone: 'danger' },
  ];
  picked = '';
  tab = 'a';
  page = 1;
  day: string | null = '2026-09-29';
}

describe('Navegación', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [NavigationFixture] }));

  it('ds-menu: el foco recorre las acciones, Escape vuelve al botón y avisa la elegida', async () => {
    const fixture = await render(NavigationFixture);
    const root: HTMLElement = fixture.nativeElement;
    const trigger = root.querySelector<HTMLButtonElement>('ds-menu button')!;
    expect(trigger.getAttribute('aria-haspopup')).toBe('menu');
    trigger.focus();
    key(trigger, 'ArrowDown');
    expect(root.querySelector('[role="menu"]')!.classList).toContain('ds-popover--placed');
    fixture.detectChanges();
    let items = root.querySelectorAll<HTMLElement>('[role="menuitem"]');
    expect(root.querySelector('[role="menu"]')!.getAttribute('aria-labelledby')).toBe(trigger.id);
    expect(active()).toBe(items[0]);
    key(items[0], 'ArrowUp');
    expect(active()).toBe(items[2]);
    key(items[2], 'Escape');
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(active()).toBe(trigger);

    trigger.click();
    fixture.detectChanges();
    items = root.querySelectorAll<HTMLElement>('[role="menuitem"]');
    items[1].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.picked).toBe('');
    items[2].click();
    fixture.detectChanges();
    expect(fixture.componentInstance.picked).toBe('revoke');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('ds-tabs: flechas, Inicio y Fin cambian la pestaña y su panel', async () => {
    const fixture = await render(NavigationFixture);
    const root: HTMLElement = fixture.nativeElement;
    const tabs = root.querySelectorAll<HTMLElement>('[role="tab"]');
    const panels = root.querySelectorAll<HTMLElement>('[role="tabpanel"]');
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(panels[0].getAttribute('aria-labelledby')).toBe(tabs[0].id);
    expect(panels[1].hidden).toBe(true);
    tabs[0].focus();
    key(tabs[0], 'ArrowRight');
    fixture.detectChanges();
    expect(fixture.componentInstance.tab).toBe('b');
    expect(active()).toBe(tabs[1]);
    expect(panels[1].hidden).toBe(false);
    key(tabs[1], 'End');
    key(active(), 'ArrowRight');
    fixture.detectChanges();
    expect(fixture.componentInstance.tab).toBe('a');
  });

  it('ds-pagination, ds-steps y ds-progress exponen su estado', async () => {
    const fixture = await render(NavigationFixture);
    const root: HTMLElement = fixture.nativeElement;
    expect(root.querySelector('ds-pagination nav')!.getAttribute('aria-label')).toBe('Páginas');
    expect(root.querySelector('.ds-pagination__summary')!.textContent).toContain('1–10 de 42');
    expect(root.querySelector('[aria-current="page"]')!.textContent!.trim()).toBe('1');
    expect(root.querySelector<HTMLButtonElement>('[aria-label="Página anterior"]')!.disabled).toBe(
      true,
    );
    root.querySelector<HTMLButtonElement>('[aria-label="Página siguiente"]')!.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.page).toBe(2);
    expect(root.querySelector('.ds-pagination__summary')!.textContent).toContain('11–20 de 42');

    const steps = root.querySelectorAll('ds-steps li');
    expect(steps[1].getAttribute('aria-current')).toBe('step');
    expect(steps[0].textContent).toContain('completado');

    const [known, unknown] = root.querySelectorAll('[role="progressbar"]');
    expect(known.getAttribute('aria-valuenow')).toBe('30');
    expect(root.querySelector('#' + known.getAttribute('aria-labelledby'))!.textContent).toBe(
      'Consultando',
    );
    expect(unknown.hasAttribute('aria-valuenow')).toBe(false);
  });

  it('ds-calendar: días en español, límites y movimiento por teclado', async () => {
    const fixture = await render(NavigationFixture);
    const root: HTMLElement = fixture.nativeElement;
    const calendar = root.querySelector<HTMLElement>('ds-calendar')!;
    expect(calendar.querySelector('.ds-calendar__title')!.textContent).toContain(
      'Septiembre de 2026',
    );
    const selected = calendar.querySelector<HTMLElement>('[data-date="2026-09-29"]')!;
    expect(selected.getAttribute('tabindex')).toBe('0');
    expect(selected.getAttribute('aria-label')).toBe('martes, 29 de septiembre de 2026');
    expect(selected.closest('td')!.getAttribute('aria-selected')).toBe('true');
    const blocked = calendar.querySelector<HTMLElement>('[data-date="2026-09-09"]')!;
    expect(blocked.getAttribute('aria-disabled')).toBe('true');
    blocked.click();
    expect(fixture.componentInstance.day).toBe('2026-09-29');

    selected.focus();
    key(selected, 'ArrowDown');
    key(active(), 'End');
    fixture.detectChanges();
    expect(active().dataset['date']).toBe('2026-10-11');
    key(active(), 'PageDown');
    fixture.detectChanges();
    expect(active().dataset['date']).toBe('2026-10-20');
    expect(
      calendar.querySelector<HTMLButtonElement>('[aria-label="Mes siguiente"]')!.disabled,
    ).toBe(true);
    active().click();
    expect(fixture.componentInstance.day).toBe('2026-10-20');
  });
});

describe('Paneles flotantes', () => {
  it('se abren debajo y pasan arriba cuando abajo no caben', () => {
    const viewport = { width: 800, height: 600 };
    const panel = { width: 150, height: 300 };
    expect(placePanel({ top: 100, bottom: 144, left: 20, width: 200 }, viewport, panel)).toEqual({
      top: 148,
      left: 20,
      minWidth: 200,
      maxHeight: 444,
      above: false,
    });
    const above = placePanel({ top: 500, bottom: 544, left: 700, width: 200 }, viewport, panel);
    expect(above.above).toBe(true);
    expect(above.top).toBe(196);
    expect(above.left).toBe(592);
  });
});
