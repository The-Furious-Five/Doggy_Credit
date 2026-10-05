import { Routes } from '@angular/router';
import { App } from './app';
import { authGuard } from './auth.guard';
import { Landing } from './landing/landing';

const page = (title: string) => `DoggyCredit · ${title}`;

export const routes: Routes = [
  { path: '', component: Landing, title: 'DoggyCredit · Decisiones de crédito con datos' },
  { path: 'acceso', component: App, title: page('Acceso seguro') },
  { path: 'callback', component: App, title: page('Iniciando sesión') },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () => import('./console/console').then((m) => m.Console),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'resumen' },
      { path: 'resumen', title: page('Resumen'), loadComponent: () => import('./console/overview').then((m) => m.Overview) },
      { path: 'clientes', title: page('Clientes'), loadComponent: () => import('./console/clients').then((m) => m.Clients) },
      { path: 'clientes/:id', title: page('Perfil financiero'), loadComponent: () => import('./console/client-profile').then((m) => m.ClientProfile) },
      { path: 'evaluaciones/nueva', title: page('Nueva evaluación'), loadComponent: () => import('./console/evaluation').then((m) => m.Evaluation) },
      { path: 'productos', title: page('Productos'), loadComponent: () => import('./console/products').then((m) => m.Products) },
      { path: 'politicas', title: page('Políticas'), loadComponent: () => import('./console/policies').then((m) => m.Policies) },
      { path: 'analitica', title: page('Analítica'), loadComponent: () => import('./console/analytics').then((m) => m.Analytics) },
      { path: 'integraciones', title: page('Integraciones'), loadComponent: () => import('./console/integrations').then((m) => m.Integrations) },
      { path: 'desarrolladores', title: page('Desarrolladores'), loadComponent: () => import('./console/developers').then((m) => m.Developers) },
      { path: 'configuracion', title: page('Configuración'), loadComponent: () => import('./console/settings').then((m) => m.Settings) },
    ],
  },
  {
    path: 'design-system',
    loadComponent: () => import('./design-system-docs/catalog').then((m) => m.DesignSystemCatalog),
    title: page('Design System'),
  },
  { path: '**', redirectTo: '' },
];
