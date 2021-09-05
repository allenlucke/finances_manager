import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BudgetBalanceSheetComponent } from './component/budget-balance-sheet/budget-balance-sheet.component';
import { HelloComponent } from './component/hello/hello.component';
import { HomeComponent } from './component/home/home.component';
import { LoginComponent } from './component/login/login.component';
import { RegisterComponent } from './component/register/register.component';
import { TestBalSheetComponent } from './component/test-bal-sheet/test-bal-sheet.component';
import { AuthGuard } from './_helpers/auth.guard';


const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent},
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'hello', component: HelloComponent, canActivate:[AuthGuard] },
  { path: 'budgetBalanceSheet', component: BudgetBalanceSheetComponent, canActivate:[AuthGuard] },

  { path: 'test', component: TestBalSheetComponent, canActivate:[AuthGuard] },
  // otherwise redirect to home
  { path: '**', redirectTo: '' }
];



export const AppRoutingModule = RouterModule.forRoot(routes);