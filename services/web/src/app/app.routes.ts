import { Routes } from '@angular/router';
import { signedInGuard } from './core/signed-in-guard';
import { signedOutGuard } from './core/signed-out-guard';

/**
 * Feature routes are lazily loaded. It keeps the initial bundle to the shell plus the login screen,
 * which is all an unauthenticated visitor can use anyway.
 *
 * <p>Every route carries a `title`, which Angular's TitleStrategy writes to the document. Every
 * page used to share the tab title "finances_manager"; for someone with six of these open, or
 * using a screen reader, the title is how you tell them apart.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    title: 'Sign in · Finances',
    canActivate: [signedOutGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    title: 'Dashboard · Finances',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
  },
  {
    path: 'transactions',
    title: 'Transactions · Finances',
    canActivate: [signedInGuard],
    loadComponent: () =>
      import('./features/transactions/transactions').then((m) => m.TransactionsComponent),
  },
  {
    path: 'accounts',
    title: 'Accounts · Finances',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/accounts/accounts').then((m) => m.AccountsComponent),
  },
  {
    path: 'budget',
    title: 'Budget · Finances',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/budget/budget').then((m) => m.BudgetComponent),
  },
  {
    path: 'markets',
    title: 'Markets · Finances',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/markets/markets').then((m) => m.MarketsComponent),
  },
  {
    path: 'import',
    title: 'Import · Finances',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/import/import').then((m) => m.ImportComponent),
  },
  {
    path: 'security',
    title: 'Security · Finances',
    canActivate: [signedInGuard],
    loadComponent: () => import('./features/security/security').then((m) => m.SecurityComponent),
  },
  { path: '**', redirectTo: 'dashboard' },
];
