import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DsBadge,
  DsBrand,
  DsIcon,
  DsIconName,
  DsLink,
  DsMarquee,
  DsMarqueeItem,
  DsReveal,
  DsSlide,
  DsSlides,
  DsProgress,
  DsStat,
  DsThemeToggle,
} from '../design-system';

const item = (icon: DsIconName, title: string, text: string) => ({ icon, title, text });
const logo = (label: string, file: string): DsMarqueeItem => ({ label, image: `integraciones/${file}` });

// Textos de marketing de la landing. La consola lee plan, cuota y catálogo de productos de la API (E-09).
type ProductKind = 'micro' | 'eco' | 'pyme';

const products: Record<
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

const plans = [
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

@Component({
  selector: 'dc-landing',
  imports: [RouterLink, DsBadge, DsBrand, DsIcon, DsLink, DsMarquee, DsProgress, DsReveal, DsSlide, DsSlides, DsStat, DsThemeToggle],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  readonly products = products;
  readonly productKinds = Object.keys(products) as ProductKind[];
  readonly plans = plans;
  /** Logos reales de entidades bolivianas y proveedores, solo como ejemplo (ver public/landing/CREDITOS.md). */
  readonly integrations: { title: string; items: DsMarqueeItem[] }[] = [
    {
      title: 'Bancos',
      items: [
        logo('Banco Unión', 'union.png'),
        logo('Banco Mercantil Santa Cruz', 'mercantil.svg'),
        logo('BCP', 'bcp.svg'),
        logo('Banco Nacional de Bolivia', 'bnb.svg'),
        logo('Banco BISA', 'bisa.svg'),
        logo('Banco Económico', 'economico.webp'),
        logo('Banco Ganadero', 'ganadero.png'),
        logo('BancoSol', 'bancosol.svg'),
        logo('Banco FIE', 'fie.svg'),
        logo('Banco Fortaleza', 'fortaleza.svg'),
        logo('Banco PyME Ecofuturo', 'ecofuturo.svg'),
      ],
    },
    { title: 'Burós de crédito', items: [logo('Infocenter', 'infocenter.png')] },
    {
      title: 'Billeteras y pagos',
      items: [
        logo('Tigo Money', 'tigo.svg'),
        logo('Yape Bolivia', 'yape.svg'),
        logo('Visa', 'visa.svg'),
        logo('Mastercard', 'mastercard.svg'),
      ],
    },
    {
      title: 'Facturación y ERP',
      items: [
        logo('Impuestos Nacionales', 'impuestos.png'),
        logo('SAP', 'sap.svg'),
        logo('Odoo', 'odoo.svg'),
      ],
    },
  ];
  readonly productImages: Record<ProductKind, { src: string; alt: string }> = {
    micro: { src: 'landing/mercado-entrada.jpg', alt: 'Puestos de venta en la entrada del Mercado Rodríguez, La Paz' },
    eco: { src: 'landing/paneles-solares.jpg', alt: 'Paneles solares sobre un techo' },
    pyme: { src: 'landing/santa-cruz.jpg', alt: 'Edificios del norte de Santa Cruz de la Sierra' },
  };
  readonly steps = [
    item('plug', 'Conecta', 'Bancos, burós, wallets y tus propios sistemas por API, sin reemplazar nada.'),
    item('spark', 'Analiza', 'Unificamos los datos en un perfil financiero con score y necesidades detectadas.'),
    item('bolt', 'Decide', 'Recomendaciones y políticas automáticas, siempre con su explicación.'),
  ];
  readonly features = [
    item('user', 'Perfil financiero 360', 'Ingresos, deudas y comportamiento de pago en una sola vista.'),
    item('chart', 'Scoring explicable', 'Cada score muestra los factores que lo componen y sus fuentes.'),
    item('search', 'Necesidades de crédito', 'Detecta para qué necesita financiamiento cada cliente.'),
    item('bolt', 'Decisiones automatizadas', 'Reglas por producto con revisión humana donde importa.'),
    item('grid', 'Analytics', 'Conversión, cartera recomendada y desempeño de los modelos.'),
    item('code', 'API primero', 'REST, webhooks y claves por entorno para integrar en días.'),
  ];
  readonly security = [
    item('building', 'Multi-tenant aislado', 'Datos, usuarios y configuración separados por institución.'),
    item('lock', 'Cifrado siempre', 'En tránsito y en reposo, con claves gestionadas.'),
    item('shield', 'Consentimiento y auditoría', 'Cada consulta queda registrada con su finalidad.'),
    item('flask', 'Preparado para ML', 'Modelos versionados, monitoreados y explicables.'),
  ];
}
