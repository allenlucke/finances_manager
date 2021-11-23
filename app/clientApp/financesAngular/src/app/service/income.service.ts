import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

import { environment } from 'src/environments/environment';
import { BudgetIncomeCategoryWithName } from '../_models/budget-income-category-with-name';
import { IncomeCategory } from '../_models/income-category';

@Injectable({
  providedIn: 'root'
})
export class IncomeService {

  // All Income Categories data store values
  private _allIncomeCats = new BehaviorSubject<IncomeCategory[]>([]);
  private allIncomeCatsDataStore: { allIncomeCats: IncomeCategory[] } = { allIncomeCats: [] };
  readonly allIncomeCats = this._allIncomeCats.asObservable();

  constructor(private http: HttpClient) { }

  //Get all income categories assigned to the user
  getAllIncomeCat() {
    this.http.get<IncomeCategory[]>(`${environment.apiUrl}/getAllIncomeCats`).subscribe(
      data => {
        this.allIncomeCatsDataStore.allIncomeCats = data;
        this._allIncomeCats.next(Object.assign({}, this.allIncomeCatsDataStore).allIncomeCats);
      }
    )
  }

  //Currently only called by one component based on input change, not observed
  getBudgetIncCatsWithNameByDate(date: Date) {
    return this.http.get<BudgetIncomeCategoryWithName[]>(`${environment.apiUrl}/getBudgetIncCatsWithNameByDate?date=` + date);
  }

  addIncomeCatRetId(name: string, usersId: number ) {
    this.http.post<any>(`${environment.apiUrl}/addIncomeCatReturnId`, { name, usersId })
      .subscribe(
        data => {
          this._allIncomeCats.next(Object.assign({}, this.allIncomeCatsDataStore).allIncomeCats);
          //Update services based on change
          this.getAllIncomeCat();
          //Return new income category id
          return data;
        },
        error => console.log('Could not create income category')
      );
  }

  //Post new Income Item
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
    { budgetIncomeCategoryId, name, receivedDate, amountExpected, amountReceived, accountId, usersId })
  }

}
