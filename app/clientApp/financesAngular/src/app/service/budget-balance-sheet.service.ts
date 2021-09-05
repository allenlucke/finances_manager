import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { BudgetBalanceSheet } from '../_models/budget-balance-sheet';

@Injectable({
  providedIn: 'root'
})
export class BudgetBalanceSheetService {

  constructor(private http: HttpClient) { }

  getBudgetBalanceSheetByPeriod() {
    return this.http.get<BudgetBalanceSheet[]>(`${environment.apiUrl}/getBudgetBalanceSheetByPeriod?periodId=1`);
}
}
