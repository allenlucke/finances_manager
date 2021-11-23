import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './component/login/login.component';
import { RegisterComponent } from './component/register/register.component';
import { AuthGuard } from './_helpers/auth.guard';
import { AccountBalanceSheetComponent } from './component/account-balance-sheet/account-balance-sheet.component';
import { HomeComponent } from './component/home/home.component';
//Income Imports
import { IncomeComponent } from './component/_incomeComponent/income/income.component';
import { IncomeCategoryListComponent } from './component/_incomeComponent/income-category-list/income-category-list.component';
import { IncomeCategoryPostComponent } from './component/_incomeComponent/income-category-post/income-category-post.component';
import { IncomeItemPostComponent } from './component/_incomeComponent/income-item-post/income-item-post.component';
//Expense Imports
import { ExpensesComponent } from './component/_expenseComponent/expenses/expenses.component';
import { ExpenseCategoryListComponent } from './component/_expenseComponent/expense-category-list/expense-category-list.component';
import { ExpenseCategoryPostComponent } from './component/_expenseComponent/expense-category-post/expense-category-post.component';
import { ExpenseItemPostComponent } from './component/_expenseComponent/expense-item-post/expense-item-post.component';
//Period Imports
import { PeriodComponent } from './component/_periodComponent/period/period.component';
import { PeriodListComponent } from './component/_periodComponent/period-list/period-list.component';
import { PeriodPostComponent } from './component/_periodComponent/period-post/period-post.component';
//Budget Imports
import { BudgetComponent } from './component/_budgetComponent/budget/budget.component';
import { BudgetListComponent } from './component/_budgetComponent/budget-list/budget-list.component';
import { BudgetCloneComponent } from './component/_budgetComponent/budget-clone/budget-clone.component';
import { BudgetPostComponent } from './component/_budgetComponent/budget-post/budget-post.component';
import { BudgetExpenseCategoryPostComponent } from './component/_budgetComponent/budget-expense-category-post/budget-expense-category-post.component';
import { BudgetBalanceSheetComponent } from './component/budget-balance-sheet/budget-balance-sheet.component';
//Account Imports
import { AccountComponent } from './component/_accountComponent/account/account.component';
import { AccountListComponent } from './component/_accountComponent/account-list/account-list.component';
import { AccountPostComponent } from './component/_accountComponent/account-post/account-post.component';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent},
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'accountBalanceSheet', component: AccountBalanceSheetComponent, canActivate: [AuthGuard]},
  //Income Components
  { path: 'income', component: IncomeComponent, canActivate: [AuthGuard]},
  { path: 'incomeCatList', component: IncomeCategoryListComponent, canActivate: [AuthGuard]},
  { path: 'incomeCatPost', component: IncomeCategoryPostComponent, canActivate: [AuthGuard]},
  { path: 'incomeItemPost', component: IncomeItemPostComponent, canActivate: [AuthGuard]},
  //Expense Components
  { path: 'expenses', component: ExpensesComponent, canActivate:[AuthGuard]},
  { path: 'expenseCatList', component: ExpenseCategoryListComponent, canActivate: [AuthGuard]},
  { path: 'expenseCatPost', component: ExpenseCategoryPostComponent, canActivate: [AuthGuard]},
  { path: 'expenseItemPost', component: ExpenseItemPostComponent, canActivate: [AuthGuard]},
  //Period Components
  { path: 'period', component: PeriodComponent, canActivate: [AuthGuard]},
  { path: 'periodList', component: PeriodListComponent, canActivate: [AuthGuard]},
  { path: 'periodPost', component: PeriodPostComponent, canActivate: [AuthGuard]},
  //Budget Components
  { path: 'budgets', component: BudgetComponent, canActivate: [AuthGuard]},
  { path: 'budgetList', component: BudgetListComponent, canActivate: [AuthGuard]},
  { path: 'budgetClone', component: BudgetCloneComponent, canActivate: [AuthGuard]},
  { path: 'budgetPost', component: BudgetPostComponent, canActivate: [AuthGuard]},
  { path: 'budgetExpCatPost', component: BudgetExpenseCategoryPostComponent, canActivate: [AuthGuard]},
  { path: 'budgetBalanceSheet', component: BudgetBalanceSheetComponent, canActivate: [AuthGuard] },
  //Account Components
  { path: 'account', component: AccountComponent, canActivate: [AuthGuard]},
  { path: 'accountList', component: AccountListComponent, canActivate: [AuthGuard]},
  { path: 'accountPost', component: AccountPostComponent, canActivate: [AuthGuard]},
  // otherwise redirect to home
  { path: '**', redirectTo: '' }
];


export const AppRoutingModule = RouterModule.forRoot(routes);