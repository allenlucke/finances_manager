import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { ReactiveFormsModule } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule} from  '@angular/material/list';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

// HttpClient module for RESTful API
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
//
// Components
//
import { AppComponent } from './app.component';
import { JwtInterceptor } from 'src/app/_helpers/jwt.interceptor';
import { ErrorInterceptor } from 'src/app/_helpers/error.interceptor';
import { HttpErrorInterceptor } from './_helpers/http-error.interceptor';
import { FooterComponent } from './footer/footer.component';
import { HomeComponent } from './component/home/home.component';
import { LoginComponent } from './component/login/login.component';
import { AppRoutingModule } from './app-routing.module';
import { RegisterComponent } from './component/register/register.component';
import { ModalComponent } from './component/modal/modal.component';
//Sheet Components
import { BudgetBalanceSheetComponent } from './component/budget-balance-sheet/budget-balance-sheet.component';
import { AccountBalanceSheetComponent } from './component/account-balance-sheet/account-balance-sheet.component';
//Income Components
import { IncomeComponent } from './component/_incomeComponent/income/income.component';
import { IncomeCategoryListComponent } from './component/_incomeComponent/income-category-list/income-category-list.component';
import { IncomeCategoryPostComponent } from './component/_incomeComponent/income-category-post/income-category-post.component';
import { IncomeItemPostComponent } from './component/_incomeComponent/income-item-post/income-item-post.component';
//Expense Components
import { ExpensesComponent } from './component/_expenseComponent/expenses/expenses.component';
import { ExpenseCategoryListComponent } from './component/_expenseComponent/expense-category-list/expense-category-list.component';
import { ExpenseCategoryPostComponent } from './component/_expenseComponent/expense-category-post/expense-category-post.component';
import { ExpenseItemPostComponent } from './component/_expenseComponent/expense-item-post/expense-item-post.component';
//Period Components
import { PeriodComponent } from './component/_periodComponent/period/period.component';
import { PeriodPostComponent } from './component/_periodComponent/period-post/period-post.component';
import { PeriodListComponent } from './component/_periodComponent/period-list/period-list.component';
//Budget Components
import { BudgetComponent } from './component/_budgetComponent/budget/budget.component';
import { BudgetCloneComponent } from './component/_budgetComponent/budget-clone/budget-clone.component';
import { BudgetPostComponent } from './component/_budgetComponent/budget-post/budget-post.component';
import { BudgetExpenseCategoryPostComponent } from './component/_budgetComponent/budget-expense-category-post/budget-expense-category-post.component';
import { BudgetListComponent } from './component/_budgetComponent/budget-list/budget-list.component';
//Account Components
import { AccountComponent } from './component/_accountComponent/account/account.component';
import { AccountListComponent } from './component/_accountComponent/account-list/account-list.component';
import { AccountPostComponent } from './component/_accountComponent/account-post/account-post.component';


@NgModule({
  declarations: [
    AppComponent,
    FooterComponent,
    HomeComponent,
    LoginComponent,
    RegisterComponent,
    BudgetBalanceSheetComponent,
    AccountBalanceSheetComponent,
    ExpensesComponent,
    IncomeComponent,
    ModalComponent,
    PeriodComponent,
    BudgetComponent,
    BudgetCloneComponent,
    BudgetPostComponent,
    BudgetExpenseCategoryPostComponent,
    BudgetListComponent,
    PeriodPostComponent,
    PeriodListComponent,
    ExpenseCategoryPostComponent,
    ExpenseItemPostComponent,
    ExpenseCategoryListComponent,
    IncomeCategoryListComponent,
    IncomeCategoryPostComponent,
    IncomeItemPostComponent,
    AccountComponent,
    AccountListComponent,
    AccountPostComponent,
  ],
  imports: [
    FormsModule,
    MatDialogModule,
    ReactiveFormsModule,
    BrowserModule,
    BrowserAnimationsModule,
    HttpClientModule,
    AppRoutingModule,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: HttpErrorInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent],
  entryComponents: [ModalComponent]
})
export class AppModule { }
