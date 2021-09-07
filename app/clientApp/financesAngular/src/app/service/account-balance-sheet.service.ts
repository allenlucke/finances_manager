import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { AccountBalanceSheet } from '../_models/account-balance-sheet';

@Injectable({
  providedIn: 'root'
})
export class AccountBalanceSheetService {

  constructor(private http: HttpClient) { }

  getAcctBalSheet() {
    return this.http.get<AccountBalanceSheet[]>(`${environment.apiUrl}/getAcctBalSheet?acctId=1&periodId=2`);
  }
}
