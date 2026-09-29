import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { DesignSystemCatalog } from './catalog';
import { DsThemeService } from '../design-system';

describe('Catálogo del Design System', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      imports: [DesignSystemCatalog],
      providers: [provideRouter([])],
    }),
  );
  afterEach(() => {
    TestBed.inject(DsThemeService).preference.set('system');
    document.documentElement.removeAttribute('data-theme');
    localStorage.removeItem('dc-theme');
  });
  it('se abre sin proveedor OIDC ni peticiones a la API', async () => {
    const fixture = TestBed.createComponent(DesignSystemCatalog);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Menos ruido.');
    expect(fixture.nativeElement.querySelectorAll('section').length).toBe(10);
  });
  it('valida el formulario y lleva el foco al primer campo inválido', async () => {
    const fixture = TestBed.createComponent(DesignSystemCatalog);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('ds-combobox input');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(input);
  });
  it('no usa controles con interfaz del navegador', async () => {
    const fixture = TestBed.createComponent(DesignSystemCatalog);
    fixture.detectChanges();
    await fixture.whenStable();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('select, datalist, input[type="date"], input[type="number"]')).toBeNull();
    expect(page.querySelectorAll('ds-select, ds-combobox, ds-date-picker, ds-number-field').length).toBe(6);
  });
  it('el selector de apariencia cambia realmente el tema', async () => {
    const fixture = TestBed.createComponent(DesignSystemCatalog);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.nativeElement.querySelector('input[value="dark"]').click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });
});
