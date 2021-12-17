import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { Account } from '../_models/account';
import { BehaviorSubject } from 'rxjs';
import { data } from 'jquery';

@Injectable({
  providedIn: 'root'
})
export class AccountService {

  //All Accounts data store values
  private _allAccounts = new BehaviorSubject<Account[]>([]);
  private allAccountsDataStore: { allAccounts: Account[] } = { allAccounts: [] };
  readonly allAccounts = this._allAccounts.asObservable();

  constructor(private http: HttpClient) { }

  //Get all accounts belonging to the user
  getAllAccounts() {
    this.http.get<Account[]>(`${environment.apiUrl}/getAllAccounts`).subscribe(
      data => {
        console.log("Data" + JSON.stringify(data))
        this.allAccountsDataStore.allAccounts = data;
        this._allAccounts.next(Object.assign({}, this.allAccountsDataStore).allAccounts);
      }
    )
  }

  //Add new account
  addAccountRetId(name: string, usersId: number, isCredit: boolean, creationDate: Date) {
    this.http.post<any>(`${environment.apiUrl}/addAccountReturningId`, { name, usersId, isCredit, creationDate })
    .subscribe(
      data => {
        this._allAccounts.next(Object.assign({}, this.allAccountsDataStore).allAccounts);
        console.log("Data" + JSON.stringify(data))
        //Update services based on change
        this.getAllAccounts();
        //Return new account id
        return data;
      },
      error => console.log('Could not create account')
    )
  }
}
