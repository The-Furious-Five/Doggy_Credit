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
  grid: 'M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0',
  users:
    'M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm7-6.8a3.5 3.5 0 0 1 0 6.6M20 20v-1.5a3.5 3.5 0 0 0-2.5-3.35',
  chart: 'M4 4v16h16M8 16v-4m4 4V8m4 8v-6',
  plug: 'M9 3v5m6-5v5M7 8h10v3a5 5 0 0 1-10 0V8Zm5 8v5',
  code: 'm8 8-4 4 4 4m8-8 4 4-4 4M14 5l-4 14',
  sliders: 'M4 6h9m4 0h3M4 12h3m4 0h9M4 18h11m4 0h1M15 4v4M9 10v4m8 2v4',
  bolt: 'M13 3 5 13.5h6L10 21l8-10.5h-6L13 3Z',
  spark: 'M12 3v4m0 10v4M3 12h4m10 0h4M6 6l2.5 2.5m7 7L18 18M6 18l2.5-2.5m7-7L18 6',
  leaf: 'M5 20c2.5-5 6-8 10-10M6 18C4 10 9 4 20 4c0 11-6 16-14 14',
  store: 'M4 10v10h16V10M3 4h18l-1.5 6h-15L3 4Zm7 16v-5h4v5',
  coin:
    circle +
    'M14.5 9.5c-.5-1-1.4-1.5-2.5-1.5-1.4 0-2.5.8-2.5 2s1.1 1.6 2.5 2 2.5.8 2.5 2-1.1 2-2.5 2c-1.1 0-2-.5-2.5-1.5M12 6.5V8m0 8v1.5',
  wallet: 'M4 7a2 2 0 0 1 2-2h12v4M4 7v11a2 2 0 0 0 2 2h14V9H6a2 2 0 0 1-2-2Zm12 7.5h.01',
  database:
    'M4 6c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3Zm0 0v12c0 1.7 3.6 3 8 3s8-1.3 8-3V6M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3',
  lock: 'M7 11V8a5 5 0 0 1 10 0v3M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1Z',
  key: 'M15 13a5 5 0 1 0-4.6-3L3 17.4V21h3.6v-2h2v-2h2l1.4-1.4A5 5 0 0 0 15 13Zm1.5-5.5v.01',
  sun: 'M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0-14v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  logout: 'M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4m5-12 4 4-4 4m4-4H9',
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
