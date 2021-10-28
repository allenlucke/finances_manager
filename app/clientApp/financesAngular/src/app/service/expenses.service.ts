import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { ExpenseCategory } from '../_models/expense-category';
import { ExpenseItem } from '../_models/expense-item';

@Injectable({
  providedIn: 'root'
})
export class ExpensesService {

  constructor(private http: HttpClient) { }

  getAllExpCat() {
    return this.http.get<ExpenseCategory[]>(`${environment.apiUrl}/getAllExpCat`);
  }

  addExpCatRetId(name: string, usersId: number ) {
    return this.http.post<any>(`${environment.apiUrl}/addExpCatRetId`, { name, usersId })
  }

  addExpItemRetId(expItem: ExpenseItem) {
    return this.http.post<any>(`${environment.apiUrl}/addExpItemRetId`, { expItem })
  }

}
