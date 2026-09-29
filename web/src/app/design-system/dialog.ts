import { Component, ElementRef, OnDestroy, inject, input, output, viewChild } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { DsButton } from './primitives';
import { DsIcon } from './icon';
let dialogSequence = 0;
@Component({
  selector: 'ds-dialog',
  imports: [DsButton, DsIcon],
  template: `
    <dialog #native class="ds-dialog" [attr.aria-labelledby]="titleId" (close)="onClose()">
      <div class="ds-dialog__header">
        <h2 [id]="titleId">{{ heading() }}</h2>
        <button dsButton variant="quiet" icon aria-label="Cerrar diálogo" (click)="close()">
          <ds-icon name="close" />
        </button>
      </div>
      <div class="ds-dialog__body"><ng-content /></div>
    </dialog>
  `,
})
export class DsDialog implements OnDestroy {
  readonly heading = input.required<string>();
  readonly dismissed = output<void>();
  readonly titleId = 'ds-dialog-' + ++dialogSequence;
  private readonly native = viewChild.required<ElementRef<HTMLDialogElement>>('native');
  private readonly document = inject(DOCUMENT);
  private previousFocus: HTMLElement | null = null;
  show(): void {
    if (this.native().nativeElement.open) return;
    this.previousFocus = this.document.activeElement as HTMLElement;
    this.native().nativeElement.showModal();
  }
  close(): void {
    this.native().nativeElement.close();
  }
  onClose(): void {
    this.previousFocus?.focus();
    this.dismissed.emit();
  }
  ngOnDestroy(): void {
    if (this.native().nativeElement.open) this.close();
  }
}
