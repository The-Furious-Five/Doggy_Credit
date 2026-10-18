// Formato compartido por Clientes y Perfil del cliente (B-09).
import { Segmento } from '../api';

export const segmentLabels: Record<Segmento, string> = {
  [Segmento.Persona]: 'Persona',
  [Segmento.Emprendedor]: 'Emprendedor',
  [Segmento.Pyme]: 'PYME',
};

export const segmentLabel = (segment?: Segmento): string => segmentLabels[segment ?? Segmento.Persona];

const currencyLabel: Record<string, string> = { BOB: 'Bs', USD: 'US$' };
const amount = new Intl.NumberFormat('es-BO', { maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat('es-BO', { day: '2-digit', month: '2-digit', year: 'numeric' });

/** A-10: el servidor ya envía el monto enmascarado a quien no tiene permiso de verlo. */
export const isMasked = (value: string): boolean => value.includes('•');

/** "Bs 9.800"; "—" si el dato es desconocido (la fuente no respondió). */
export const money = (value?: { amount: string; currency: string } | null): string =>
  !value
    ? '—'
    : isMasked(value.amount)
      ? value.amount
      : `${currencyLabel[value.currency] ?? value.currency} ${amount.format(Number(value.amount))}`;

export const day = (iso?: string | null): string => (iso ? date.format(new Date(iso)) : '—');
