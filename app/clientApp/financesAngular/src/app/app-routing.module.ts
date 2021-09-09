import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AccountBalanceSheetComponent } from './component/account-balance-sheet/account-balance-sheet.component';
import { BudgetBalanceSheetComponent } from './component/budget-balance-sheet/budget-balance-sheet.component';
import { ExpensesComponent } from './component/expenses/expenses.component';
import { HelloComponent } from './component/hello/hello.component';
import { HomeComponent } from './component/home/home.component';
import { IncomeComponent } from './component/income/income.component';
import { LoginComponent } from './component/login/login.component';
import { RegisterComponent } from './component/register/register.component';
import { AuthGuard } from './_helpers/auth.guard';


const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent},
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'hello', component: HelloComponent, canActivate:[AuthGuard] },
  { path: 'budgetBalanceSheet', component: BudgetBalanceSheetComponent, canActivate:[AuthGuard] },
  { path: 'accountBalanceSheet', component: AccountBalanceSheetComponent, canActivate:[AuthGuard]},
  { path: 'income', component: IncomeComponent, canActivate:[AuthGuard]},
  { path: 'expenses', component: ExpensesComponent, canActivate:[AuthGuard]},

  // otherwise redirect to home
  { path: '**', redirectTo: '' }
];



export const AppRoutingModule = RouterModule.forRoot(routes);