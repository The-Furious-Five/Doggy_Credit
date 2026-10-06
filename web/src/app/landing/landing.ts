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
import { ProductKind, plans, productKinds, products } from '../data';

const item = (icon: DsIconName, title: string, text: string) => ({ icon, title, text });
const logo = (label: string, file: string): DsMarqueeItem => ({ label, image: `integraciones/${file}` });

@Component({
  selector: 'dc-landing',
  imports: [RouterLink, DsBadge, DsBrand, DsIcon, DsLink, DsMarquee, DsProgress, DsReveal, DsSlide, DsSlides, DsStat, DsThemeToggle],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  readonly products = products;
  readonly productKinds = productKinds;
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
