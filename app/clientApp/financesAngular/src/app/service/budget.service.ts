import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { Budget } from '../_models/budget';

@Injectable({
  providedIn: 'root'
})
export class BudgetService {

  constructor(private http: HttpClient) { }

  getAllBudgets() {
    return this.http.get<Budget[]>(`${environment.apiUrl}/getAllBudgets`);
  }

  addBudgetRetId(name: string, periodId: number, usersId: number) {
    return this.http.post<any>(`${environment.apiUrl}/addBudgetRetId`, { name, periodId, usersId })
  }

  cloneBudget(templateBudgetId : number, name: string, periodId: number, usersId: number) {
    return this.http.post<any>(`${environment.apiUrl}/cloneBudget?templateBudgetId=` + templateBudgetId, {name, periodId, usersId})
  }
}
