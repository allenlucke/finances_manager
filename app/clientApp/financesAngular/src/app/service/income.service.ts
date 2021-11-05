import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { environment } from 'src/environments/environment';
import { BudgetIncomeCategoryWithName } from '../_models/budget-income-category-with-name';
import { IncomeCategory } from '../_models/income-category';

@Injectable({
  providedIn: 'root'
})
export class IncomeService {

  constructor(private http: HttpClient) { }

  getAllIncomeCat() {
    return this.http.get<IncomeCategory[]>(`${environment.apiUrl}/getAllIncomeCats`);
  }

  getBudgetIncCatsWithNameByDate(date: Date) {
    return this.http.get<BudgetIncomeCategoryWithName[]>(`${environment.apiUrl}/getBudgetIncCatsWithNameByDate?date=` + date);
  }

  addIncomeCatRetId(name: string, usersId: number ) {
    return this.http.post<any>(`${environment.apiUrl}/addIncomeCatReturnId`, { name, usersId })
  }

  addIncItemRetId(
    budgetIncomeCategoryId: number,
    name: string,
    receivedDate: string,
    amountExpected: number,
    amountReceived: number,
    accountId: number,
    usersId: number
    ) 
    {
      console.log(receivedDate)
      console.log(usersId)
    return this.http.post<any>(`${environment.apiUrl}/addIncomeItemReturnId`, 
    { 
      budgetIncomeCategoryId, 
      name, 
      receivedDate, 
      amountExpected, 
      amountReceived,
      accountId, 
      usersId 
    })
  }

}
