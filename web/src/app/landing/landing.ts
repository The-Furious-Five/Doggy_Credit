import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  DsBadge,
  DsBrand,
  DsCard,
  DsIcon,
  DsIconName,
  DsLink,
  DsProgress,
  DsStat,
} from '../design-system';
import { plans, productKinds, products } from '../data';

const item = (icon: DsIconName, title: string, text: string) => ({ icon, title, text });

@Component({
  selector: 'dc-landing',
  imports: [RouterLink, DsBadge, DsBrand, DsCard, DsIcon, DsLink, DsProgress, DsStat],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  readonly products = products;
  readonly productKinds = productKinds;
  readonly plans = plans;
  readonly sources: { icon: DsIconName; label: string }[] = [
    { icon: 'building', label: 'Bancos' },
    { icon: 'database', label: 'Burós de crédito' },
    { icon: 'wallet', label: 'Billeteras digitales' },
    { icon: 'plug', label: 'Open banking' },
    { icon: 'store', label: 'Facturación y ERP' },
  ];
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
