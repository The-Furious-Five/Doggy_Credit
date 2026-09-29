import { Component, computed, input } from '@angular/core';
const paths = {
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  check: 'm5 12 4 4L19 6',
  close: 'm6 6 12 12M18 6 6 18',
  plus: 'M12 5v14M5 12h14',
  shield: 'm12 3 8 4v5c0 5-8 9-8 9s-8-4-8-9V7l8-4Zm-4 9 3 3 5-6',
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  chevron: 'm9 5 7 7-7 7',
  moon: 'M20 14A9 9 0 0 1 10 4a9 9 0 1 0 10 10',
};
export type DsIconName = keyof typeof paths;
@Component({
  selector: 'ds-icon',
  host: { class: 'ds-icon', 'aria-hidden': 'true' },
  template: `
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.7"
      stroke-linecap="round"
      stroke-linejoin="round"
      focusable="false"
    >
      <path [attr.d]="path()" />
    </svg>
  `,
})
export class DsIcon {
  readonly name = input.required<DsIconName>();
  readonly path = computed(() => paths[this.name()]);
}
