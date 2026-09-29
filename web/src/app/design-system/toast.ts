import { Component, Injectable, OnDestroy, inject, signal } from '@angular/core';
import { DsButton } from './primitives';
import { DsIcon } from './icon';
@Injectable({ providedIn: 'root' })
export class DsToastService implements OnDestroy {
  readonly message = signal('');
  private timer?: ReturnType<typeof setTimeout>;
  show(message: string): void {
    this.dismiss();
    this.message.set(message);
    this.resume();
  }
  pause(): void {
    clearTimeout(this.timer);
  }
  resume(): void {
    this.pause();
    if (this.message()) this.timer = setTimeout(() => this.dismiss(), 8000);
  }
  dismiss(): void {
    this.pause();
    this.message.set('');
  }
  ngOnDestroy(): void {
    this.pause();
  }
}
@Component({
  selector: 'ds-toast-outlet',
  imports: [DsButton, DsIcon],
  template: `
    <div class="ds-toast-live" role="status" aria-live="polite" aria-atomic="true">
      {{ toast.message() }}
    </div>
    @if (toast.message()) {
      <div
        class="ds-toast"
        (mouseenter)="toast.pause()"
        (mouseleave)="toast.resume()"
        (focusin)="toast.pause()"
        (focusout)="toast.resume()"
      >
        <span>{{ toast.message() }}</span
        ><button
          dsButton
          variant="quiet"
          aria-label="Cerrar notificación"
          (click)="toast.dismiss()"
        >
          <ds-icon name="close" />
        </button>
      </div>
    }
  `,
})
export class DsToastOutlet {
  readonly toast = inject(DsToastService);
}
