import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';

import { environment } from 'src/environments/environment';
import { Budget } from '../_models/budget';
import { ExpenseCategory } from '../_models/expense-category';

import { PeriodService } from './period.service';

@Injectable({
  providedIn: 'root'
})
export class BudgetService {

  //All budget data store values
  private _allBudgets = new BehaviorSubject<Budget[]>([]);
  private allBudgetDataStore: { allBudgets: Budget[] } = { allBudgets: [] };
  readonly allBudgets = this._allBudgets.asObservable();

  constructor(
    private http: HttpClient,
    private periodService : PeriodService
    ) { }

  //Get list of all budgets owned by user
  getAllBudgets(){
    this.http.get<Budget[]>(`${environment.apiUrl}/getAllBudgets`).subscribe(
      data => {
        this.allBudgetDataStore.allBudgets = data;
        this._allBudgets.next(Object.assign({}, this.allBudgetDataStore).allBudgets);
      }
    )
  }

  //Add a new budget
  addBudgetRetId(name: string, periodId: number, usersId: number) {
    this.http.post<any>(`${environment.apiUrl}/addBudgetRetId`, { name, periodId, usersId })
      .subscribe(
        data => {
          this._allBudgets.next(Object.assign({}, this.allBudgetDataStore).allBudgets);
          console.log("Data" + JSON.stringify(data))
          //Update services based on change
          this.getAllBudgets();
          this.periodService.getPeriodsWithoutBudget();
          //Return new budget id
          return data;
        },
        error => console.log('Could not create budget.')
      );
  }

  //Clone an existing budget, this will create a new budget for an existing period
  cloneBudget(templateBudgetId : number, name: string, periodId: number, usersId: number) {
    this.http.post<any>(`${environment.apiUrl}/cloneBudget?templateBudgetId=` + templateBudgetId, {name, periodId, usersId})
      .subscribe(
        data => {
          this._allBudgets.next(Object.assign({}, this.allBudgetDataStore).allBudgets);
          console.log("Data" + JSON.stringify(data))
          //Update services based on change
          this.getAllBudgets();
          this.periodService.getPeriodsWithoutBudget();
          //Return new budget id
          return data;
        },
        error => console.log('Could not clone budget.')
      );  
  }

  //Add expense category to an existing budget
  addBudgetExpenseCategory(budgetId : number, expenseCategoryId : number, amountBudgeted : number, usersId : number) {
    this.http.post<any>(`${environment.apiUrl}/addBudgetExpCatReturnId`, {budgetId, expenseCategoryId, amountBudgeted, usersId})
    .subscribe(
      data => {
        console.log("Data" + JSON.stringify(data))
        //Update services based on change
        this.getAllBudgets();
        //Return new budget id
        return data;
      },
      error => console.log('Could not add budget expense category.')
    );  
  }

  //Get expense categories not currently assigned to selected budget
  //Currently only called by one component based on input change, not observed
  getExpenseCatsNotAssignedToBudget(budgetId : number) {
    return this.http.get<ExpenseCategory[]>(`${environment.apiUrl}/getExpenseCatsNotAssignedToBudget?budgetId=` + budgetId)
  }
}
