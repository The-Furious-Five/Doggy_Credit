import { Routes } from '@angular/router';
import { App } from './app';
export const routes: Routes = [
  { path: '', component: App, title: 'DoggyCredit · Acceso seguro' },
  { path: 'callback', component: App, title: 'DoggyCredit · Iniciando sesión' },
  {
    path: 'design-system',
    loadComponent: () => import('./design-system-docs/catalog').then((m) => m.DesignSystemCatalog),
    title: 'DoggyCredit · Design System',
  },
  { path: '**', redirectTo: '' },
];
