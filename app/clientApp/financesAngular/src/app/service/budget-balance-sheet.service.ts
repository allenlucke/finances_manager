import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { BudgetBalanceSheet } from '../_models/budget-balance-sheet';
import { Period } from '../_models/period';

@Injectable({
  providedIn: 'root'
})
export class BudgetBalanceSheetService {

  constructor(private http: HttpClient) { }

  getBudgetBalanceSheetByPeriod(periodId : number) {
    return this.http.get<BudgetBalanceSheet[]>(`${environment.apiUrl}/getBudgetBalanceSheetByPeriod?periodId=` + periodId);
  }

  getCurrentPeriod() {
    return this.http.get<Period[]>(`${environment.apiUrl}/getCurrentPeriod`);
  }

  getAllPeriods() {
    return this.http.get<Period[]>(`${environment.apiUrl}/getAllPeriods`);
  }

  getPeriodById(periodId : number) {
    return this.http.get<Period[]>(`${environment.apiUrl}/getPeriodById?periodId=` + periodId);
  }
}
