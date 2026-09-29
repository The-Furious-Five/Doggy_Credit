import { TestBed } from '@angular/core/testing';
import { DesignSystemCatalog } from './catalog';
import { DsThemeService } from '../design-system';

describe('Catálogo del Design System', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [DesignSystemCatalog] }));
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
    expect(fixture.nativeElement.querySelectorAll('section').length).toBe(8);
  });
  it('valida el formulario y lleva el foco al primer campo inválido', async () => {
    const fixture = TestBed.createComponent(DesignSystemCatalog);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[name="name"]');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(input);
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
