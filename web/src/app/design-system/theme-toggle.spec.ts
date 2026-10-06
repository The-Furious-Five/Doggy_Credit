import { TestBed } from '@angular/core/testing';
import { DsThemeService, DsThemeToggle } from './index';

describe('Botón de tema', () => {
  beforeEach(() => {
    localStorage.removeItem('dc-theme');
    document.documentElement.removeAttribute('data-theme');
  });

  function render() {
    const fixture = TestBed.createComponent(DsThemeToggle);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    return { fixture, button, theme: TestBed.inject(DsThemeService) };
  }

  it('alterna claro y oscuro con un clic, sin menú', () => {
    const { fixture, button, theme } = render();
    theme.preference.set('light');
    fixture.detectChanges();

    button.click();
    fixture.detectChanges();
    expect(theme.preference()).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(fixture.nativeElement.querySelector('[role="menu"]')).toBeNull();

    button.click();
    fixture.detectChanges();
    expect(theme.preference()).toBe('light');
  });

  it('en claro ofrece la luna y en oscuro el sol, con nombre accesible', () => {
    const { fixture, button, theme } = render();
    theme.preference.set('light');
    fixture.detectChanges();
    expect(button.dataset['themeState']).toBe('light');
    expect(button.getAttribute('aria-label')).toBe('Cambiar a modo oscuro');

    theme.preference.set('dark');
    fixture.detectChanges();
    expect(button.dataset['themeState']).toBe('dark');
    expect(button.getAttribute('aria-label')).toBe('Cambiar a modo claro');
    expect(button.querySelectorAll('.ds-theme-toggle__icon')).toHaveLength(2);
  });

  it('desde "según el sistema" pasa al tema contrario del que se ve', () => {
    const { button, theme } = render();
    theme.preference.set('system');
    const visible = theme.resolved();
    button.click();
    expect(theme.preference()).toBe(visible === 'dark' ? 'light' : 'dark');
  });
});
