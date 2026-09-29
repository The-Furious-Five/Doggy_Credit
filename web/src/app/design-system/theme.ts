import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';
export type DsTheme = 'system' | 'light' | 'dark';
@Injectable({ providedIn: 'root' })
export class DsThemeService {
  private readonly document = inject(DOCUMENT);
  readonly preference = signal<DsTheme>(this.read());
  constructor() {
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
  private read(): DsTheme {
    try {
      const value = this.document.defaultView?.localStorage.getItem('dc-theme');
      return value === 'light' || value === 'dark' ? value : 'system';
    } catch {
      return 'system';
    }
  }
}
