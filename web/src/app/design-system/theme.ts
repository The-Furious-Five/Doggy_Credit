import { DOCUMENT } from '@angular/common';
import { Component, Injectable, computed, effect, inject, signal } from '@angular/core';
import { DsIcon } from './icon';
import { DsButton } from './primitives';
export type DsTheme = 'system' | 'light' | 'dark';
@Injectable({ providedIn: 'root' })
export class DsThemeService {
  private readonly document = inject(DOCUMENT);
  readonly preference = signal<DsTheme>(this.read());
  private readonly systemDark = signal(this.mediaQuery()?.matches ?? false);
  /** Tema que se ve de verdad: si la preferencia es "system", el del sistema operativo. */
  readonly resolved = computed<'light' | 'dark'>(() => {
    const theme = this.preference();
    return theme === 'system' ? (this.systemDark() ? 'dark' : 'light') : theme;
  });
  constructor() {
    this.mediaQuery()?.addEventListener('change', (event) => this.systemDark.set(event.matches));
    effect(() => {
      const theme = this.preference();
      if (theme === 'system') this.document.documentElement.removeAttribute('data-theme');
      else this.document.documentElement.setAttribute('data-theme', theme);
      try {
        this.document.defaultView?.localStorage.setItem('dc-theme', theme);
      } catch {
        /* Almacenamiento opcional. */
      }
    });
  }
  /** Cambia directamente entre claro y oscuro, partiendo del tema que se ve. */
  toggle(): void {
    this.preference.set(this.resolved() === 'dark' ? 'light' : 'dark');
  }
  private mediaQuery(): MediaQueryList | undefined {
    return this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)');
  }
  private read(): DsTheme {
    try {
      const value = this.document.defaultView?.localStorage.getItem('dc-theme');
      return value === 'light' || value === 'dark' ? value : 'system';
    } catch {
      return 'system';
    }
  }
}

/**
 * Botón de tema: un clic alterna claro/oscuro. Muestra la luna en claro (pasar a
 * oscuro) y el sol en oscuro (pasar a claro), con transición entre ambos.
 */
@Component({
  selector: 'ds-theme-toggle',
  imports: [DsButton, DsIcon],
  template: `
    <button
      dsButton
      variant="quiet"
      icon
      class="ds-theme-toggle"
      [attr.data-theme-state]="theme.resolved()"
      [attr.aria-label]="theme.resolved() === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'"
      [attr.title]="theme.resolved() === 'dark' ? 'Modo claro' : 'Modo oscuro'"
      (click)="theme.toggle()"
    >
      <ds-icon class="ds-theme-toggle__icon ds-theme-toggle__icon--moon" name="moon" />
      <ds-icon class="ds-theme-toggle__icon ds-theme-toggle__icon--sun" name="sun" />
    </button>
  `,
})
export class DsThemeToggle {
  readonly theme = inject(DsThemeService);
}
