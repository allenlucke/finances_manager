import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { ExpenseCategory } from '../_models/expense-category';
import { BudgetExpenseCategory } from '../_models/budget-expense-category';
import { BudgetExpenseCategoryWithName } from '../_models/budget-expense-category-with-name';

@Injectable({
  providedIn: 'root'
})
export class ExpensesService {

  constructor(private http: HttpClient) { }

  getAllExpCat() {
    return this.http.get<ExpenseCategory[]>(`${environment.apiUrl}/getAllExpCat`);
  }

  // getBudgetExpCatsByDate(date: Date) {
  //   return this.http.get<BudgetExpenseCategory[]>(`${environment.apiUrl}/getBudgetExpCatsByDate?date=` + date);
  // }

  getBudgetExpCatsWithNameByDate(date: Date) {
    return this.http.get<BudgetExpenseCategoryWithName[]>(`${environment.apiUrl}/getBudgetExpCatsWithNameByDate?date=` + date);
  }

  addExpCatRetId(name: string, usersId: number ) {
    return this.http.post<any>(`${environment.apiUrl}/addExpCatRetId`, { name, usersId })
  }

  addExpItemRetId(
    
    budgetExpenseCategoryId: number, 
    name: string, 
    transactionDate: Date, 
    amount: number, 
    paymentToCreditAccount: boolean, 
    interestPaymentToCreditAccount: boolean,
    accountId: number, 
    usersId: number
    
    ) {
      console.log(transactionDate)
      console.log(usersId)
    return this.http.post<any>(`${environment.apiUrl}/addExpItemRetId`, 
    { 
      budgetExpenseCategoryId, 
      name, 
      transactionDate, 
      amount, 
      paymentToCreditAccount, 
      interestPaymentToCreditAccount,
      accountId, 
      usersId 
    })
  }

}
