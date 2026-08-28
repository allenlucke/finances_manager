import { Routes } from '@angular/router';
import { signedInGuard } from './core/signed-in-guard';

/**
 * Feature routes are lazily loaded. It keeps the initial bundle to the shell plus the login screen,
 * which is all an unauthenticated visitor can use anyway.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
  },
  {
    path: 'transactions',
    canActivate: [signedInGuard],
    loadComponent: () =>
      import('./features/transactions/transactions').then((m) => m.TransactionsComponent),
  },
  {
    path: 'accounts',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/accounts/accounts').then((m) => m.AccountsComponent),
  },
  {
    path: 'budget',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/budget/budget').then((m) => m.BudgetComponent),
  },
  {
    path: 'import',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/import/import').then((m) => m.ImportComponent),
  },
  {
    path: 'security',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/security/security').then((m) => m.SecurityComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
