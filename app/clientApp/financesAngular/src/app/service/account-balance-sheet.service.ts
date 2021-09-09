import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { AccountBalanceSheet } from '../_models/account-balance-sheet';
import { Account } from '../_models/account';
import { Period } from '../_models/period';

@Injectable({
  providedIn: 'root'
})
export class AccountBalanceSheetService {

  constructor(private http: HttpClient) { }

  getAcctBalSheet(accountId : number, periodId : number) {
    return this.http.get<AccountBalanceSheet[]>(`${environment.apiUrl}/getAcctBalSheet?acctId=${accountId}&periodId=${periodId}`);
  }

  getAllAccounts() {
    return this.http.get<Account[]>(`${environment.apiUrl}/getAllAccounts`);
  }

  getAllPeriods() {
    return this.http.get<Period[]>(`${environment.apiUrl}/getAllPeriods`);
  }

  getCurrentPeriod() {
    return this.http.get<Period[]>(`${environment.apiUrl}/getCurrentPeriod`);
  }

  getAccountById(accountId: number) {
    return this.http.get<Account[]>(`${environment.apiUrl}/getAccountById?acctId=${accountId}`);
  }

  getPeriodById(periodId: number) {
    return this.http.get<Period[]>(`${environment.apiUrl}/getPeriodById?periodId=${periodId}`);
  }
}
