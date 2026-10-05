// Datos de ejemplo para el esqueleto de la interfaz. No provienen de la API.
// ponytail: datos estáticos; reemplazar por los servicios de app/api al conectar cada pantalla.
import type { DsIconName, DsOption, DsTone } from './design-system';

export type ProductKind = 'micro' | 'eco' | 'pyme';

export const products: Record<
  ProductKind,
  { name: string; icon: DsIconName; summary: string; range: string; term: string }
> = {
  micro: {
    name: 'Microcrédito',
    icon: 'coin',
    summary: 'Montos pequeños y aprobación ágil para personas y emprendedores.',
    range: 'Bs 1.000 – 20.000',
    term: '3 a 24 meses',
  },
  eco: {
    name: 'Crédito ecológico',
    icon: 'leaf',
    summary: 'Financia paneles solares, eficiencia energética y movilidad limpia.',
    range: 'Bs 10.000 – 150.000',
    term: '12 a 60 meses',
  },
  pyme: {
    name: 'Crédito PYME',
    icon: 'store',
    summary: 'Capital de trabajo e inversión para pequeñas y medianas empresas.',
    range: 'Bs 50.000 – 700.000',
    term: '6 a 84 meses',
  },
};
export const productKinds = Object.keys(products) as ProductKind[];

export interface Client {
  id: string;
  name: string;
  document: string;
  segment: 'Persona' | 'Emprendedor' | 'PYME';
  score: number;
  need: string;
  product: ProductKind;
  status: { label: string; tone: DsTone };
  updated: string;
}

export const clients: Client[] = [
  { id: 'c-1042', name: 'Lucía Fernández', document: '•••• 4821', segment: 'Persona', score: 742, need: 'Eficiencia energética', product: 'eco', status: { label: 'Recomendado', tone: 'success' }, updated: '30/09/2026' },
  { id: 'c-1041', name: 'Ferretería El Puente', document: '•••• 1190', segment: 'PYME', score: 688, need: 'Capital de trabajo', product: 'pyme', status: { label: 'En revisión', tone: 'warning' }, updated: '30/09/2026' },
  { id: 'c-1039', name: 'Marco Quispe', document: '•••• 7734', segment: 'Emprendedor', score: 615, need: 'Inventario', product: 'micro', status: { label: 'Recomendado', tone: 'success' }, updated: '29/09/2026' },
  { id: 'c-1036', name: 'Agroservicios Sur', document: '•••• 5602', segment: 'PYME', score: 701, need: 'Riego tecnificado', product: 'eco', status: { label: 'Recomendado', tone: 'success' }, updated: '28/09/2026' },
  { id: 'c-1033', name: 'Daniela Rojas', document: '•••• 3318', segment: 'Persona', score: 534, need: 'Consolidar deudas', product: 'micro', status: { label: 'Datos incompletos', tone: 'danger' }, updated: '27/09/2026' },
  { id: 'c-1030', name: 'Panadería Aurora', document: '•••• 9045', segment: 'Emprendedor', score: 659, need: 'Equipamiento', product: 'micro', status: { label: 'En revisión', tone: 'warning' }, updated: '26/09/2026' },
  { id: 'c-1027', name: 'Textiles Andinos', document: '•••• 2287', segment: 'PYME', score: 720, need: 'Expansión', product: 'pyme', status: { label: 'Recomendado', tone: 'success' }, updated: '25/09/2026' },
];

export const needs = [
  { label: 'Capital de trabajo', value: 42 },
  { label: 'Consumo y consolidación', value: 28 },
  { label: 'Eficiencia energética', value: 18 },
  { label: 'Inversión productiva', value: 12 },
];

export interface Connector {
  name: string;
  category: 'Banco' | 'Buró' | 'Wallet' | 'Contabilidad' | 'Open banking';
  icon: DsIconName;
  description: string;
  status: 'connected' | 'available' | 'pro';
}

export const connectors: Connector[] = [
  { name: 'Buró de crédito nacional', category: 'Buró', icon: 'database', description: 'Historial, deudas vigentes y comportamiento de pago.', status: 'connected' },
  { name: 'Core bancario', category: 'Banco', icon: 'building', description: 'Cuentas, saldos y movimientos de tus propios clientes.', status: 'available' },
  { name: 'Billetera móvil', category: 'Wallet', icon: 'wallet', description: 'Flujos de ingresos y pagos frecuentes de la billetera.', status: 'pro' },
  { name: 'Agregador open banking', category: 'Open banking', icon: 'plug', description: 'Datos de otras entidades con consentimiento del cliente.', status: 'pro' },
  { name: 'Facturación electrónica', category: 'Contabilidad', icon: 'store', description: 'Ventas declaradas y estacionalidad de la empresa.', status: 'pro' },
  { name: 'Registro de garantías', category: 'Buró', icon: 'shield', description: 'Garantías registradas y gravámenes vigentes.', status: 'pro' },
];

export const connectorStatus: Record<Connector['status'], { label: string; tone: DsTone }> = {
  connected: { label: 'Conectado', tone: 'success' },
  available: { label: 'Disponible', tone: 'neutral' },
  pro: { label: 'Plan Pro', tone: 'info' },
};

export const plans = [
  {
    id: 'free',
    name: 'Gratis',
    price: 'Bs 0',
    period: 'para siempre',
    summary: 'Para validar DoggyCredit con tu primer equipo.',
    features: ['500 consultas al mes', 'Score básico', '1 integración', 'Hasta 3 usuarios'],
  },
  {
    id: 'pro',
    name: 'Pro',
    price: 'Bs 2.900',
    period: 'por mes',
    summary: 'Para instituciones que deciden con datos todos los días.',
    features: [
      'Consultas ilimitadas',
      'Scoring avanzado y explicable',
      'Recomendaciones inteligentes',
      'Integraciones ilimitadas',
      'Analytics y reportes',
      'APIs avanzadas y webhooks',
    ],
  },
] as const;

export const segmentOptions: DsOption<string>[] = [
  { value: '', label: 'Todos los segmentos' },
  { value: 'Persona', label: 'Persona' },
  { value: 'Emprendedor', label: 'Emprendedor' },
  { value: 'PYME', label: 'PYME' },
];

export const purposeOptions: DsOption<string>[] = [
  { value: 'trabajo', label: 'Capital de trabajo' },
  { value: 'consumo', label: 'Consumo o consolidación' },
  { value: 'eco', label: 'Eficiencia energética', description: 'Puede calificar a crédito ecológico' },
  { value: 'inversion', label: 'Inversión productiva' },
];
