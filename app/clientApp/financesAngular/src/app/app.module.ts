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

// Components
import { AppComponent } from './app.component';
import { JwtInterceptor } from 'src/app/_helpers/jwt.interceptor';
import { ErrorInterceptor } from 'src/app/_helpers/error.interceptor';
import { FooterComponent } from './footer/footer.component';
import { HomeComponent } from './component/home/home.component';
import { LoginComponent } from './component/login/login.component';
import { AppRoutingModule } from './app-routing.module';
import { RegisterComponent } from './component/register/register.component';
import { BudgetBalanceSheetComponent } from './component/budget-balance-sheet/budget-balance-sheet.component';
import { AccountBalanceSheetComponent } from './component/account-balance-sheet/account-balance-sheet.component';
import { ExpensesComponent } from './component/expenses/expenses.component';
import { IncomeComponent } from './component/income/income.component';
import { ModalComponent } from './component/modal/modal.component';
import { PeriodComponent } from './component/period/period.component';
import { BudgetComponent } from './component/budget/budget.component';
import { BudgetCloneComponent } from './component/budget-clone/budget-clone.component';
import { BudgetPostComponent } from './component/budget-post/budget-post.component';
import { BudgetExpenseCategoryPostComponent } from './component/budget-expense-category-post/budget-expense-category-post.component';


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
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent],
  entryComponents: [ModalComponent]
})
export class AppModule { }
