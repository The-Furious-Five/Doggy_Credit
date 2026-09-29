import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DsThemeService, DsToastOutlet } from './design-system';
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, DsToastOutlet],
  template: '<router-outlet /><ds-toast-outlet />',
})
export class Shell {
  private readonly theme = inject(DsThemeService);
}
