import { Component, computed, input } from '@angular/core';
const circle = 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z';
const dot = (x: number) => `M${x - 1.25} 12a1.25 1.25 0 1 0 2.5 0 1.25 1.25 0 1 0-2.5 0`;
const paths = {
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  check: 'm5 12 4 4L19 6',
  close: 'm6 6 12 12M18 6 6 18',
  plus: 'M12 5v14M5 12h14',
  minus: 'M5 12h14',
  calendar:
    'M7 3v3m10-3v3M4 10h16M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z',
  more: dot(6) + dot(12) + dot(18),
  'chevron-down': 'm6 9 6 6 6-6',
  'chevron-left': 'm15 5-7 7 7 7',
  shield: 'm12 3 8 3v6c0 5-3.6 8-8 9-4.4-1-8-4-8-9V6l8-3Zm-3.5 9 2.5 2.5 4.5-5',
  search: 'M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  chevron: 'm9 5 7 7-7 7',
  moon: 'M20 14A9 9 0 0 1 10 4a9 9 0 1 0 10 10',
  info: circle + 'M12 16.5V11M12 7.5v.01',
  success: circle + 'M8 12.5l2.7 2.7L16 9.8',
  warning:
    'M10.3 4.3 2.6 17.6A2 2 0 0 0 4.3 20.6h15.4a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0ZM12 9.5V14M12 17.2v.01',
  danger: circle + 'M12 7.5V13M12 16.5v.01',
  tray: 'M3 13.5h5l1.5 2.5h5l1.5-2.5h5M5.5 5h13L21 13.5V18a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4.5L5.5 5Z',
  building:
    'M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16m0-12h2a2 2 0 0 1 2 2v10M3 21h18M8 7h4M8 11h4M8 15h4',
  flask: 'M9 3h6m-5 0v6l-5.4 9.3A1.8 1.8 0 0 0 6.1 21h11.8a1.8 1.8 0 0 0 1.5-2.7L14 9V3M7.2 15h9.6',
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
      stroke-width="1.75"
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
