import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';

import { environment } from 'src/environments/environment';
import { ExpenseCategory } from '../_models/expense-category';
import { BudgetExpenseCategory } from '../_models/budget-expense-category';
import { BudgetExpenseCategoryWithName } from '../_models/budget-expense-category-with-name';

@Injectable({
  providedIn: 'root'
})
export class ExpensesService {

  // All Expense Categories data store values
  private _allExpenseCats = new BehaviorSubject<ExpenseCategory[]>([]);
  private allExpenseCatsDataStore: { allExpenseCats: ExpenseCategory[] } = { allExpenseCats: [] };
  readonly allExpenseCats = this._allExpenseCats.asObservable();

  constructor(private http: HttpClient) { }

  //Get all income categories assigned to the user
  getAllExpenseCat() {
    this.http.get<ExpenseCategory[]>(`${environment.apiUrl}/getAllExpCat`).subscribe(
      data => {
        this.allExpenseCatsDataStore.allExpenseCats = data;
        this._allExpenseCats.next(Object.assign({}, this.allExpenseCatsDataStore).allExpenseCats);
      }
    )
  }

  // getBudgetExpCatsByDate(date: Date) {
  //   return this.http.get<BudgetExpenseCategory[]>(`${environment.apiUrl}/getBudgetExpCatsByDate?date=` + date);
  // }

  //Currently only called by one component based on input change, not observed
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
