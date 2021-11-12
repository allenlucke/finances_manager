import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';

import { environment } from 'src/environments/environment';
import { Period } from '../_models/period';

@Injectable({
  providedIn: 'root'
})
export class PeriodService {

  // All Periods data store values
  private _allPeriods = new BehaviorSubject<Period[]>([]);
  private allPeriodsDataStore: { allPeriods: Period[] } = { allPeriods: [] };
  readonly allPeriods = this._allPeriods.asObservable();

  //Periods without budget data store values
  private _periodsWithoutBudget = new BehaviorSubject<Period[]>([]);
  private periodsWithoutBudgetDataStore: { periodsWithoutBudget: Period[] } = { periodsWithoutBudget: [] };
  readonly periodsWithoutBudget = this._periodsWithoutBudget.asObservable();

  constructor(private http: HttpClient) { }

  //Get all periods belonging to the user
  getAllPeriods() {
    this.http.get<Period[]>(`${environment.apiUrl}/getAllPeriods`).subscribe(
      data => {
        this.allPeriodsDataStore.allPeriods = data;
        this._allPeriods.next(Object.assign({}, this.allPeriodsDataStore).allPeriods);
      }
    )
  }

  //Gets all periods from a user where no budget is assigned
  getPeriodsWithoutBudget(){
    this.http.get<Period[]>(`${environment.apiUrl}/getPeriodsWithoutBudget`).subscribe(
      data => {
        this.periodsWithoutBudgetDataStore.periodsWithoutBudget = data;
        this._periodsWithoutBudget.next(Object.assign({}, this.periodsWithoutBudgetDataStore).periodsWithoutBudget);
      }
    )
  }

  //Add new period
  //Server will automatically post to accountPeriod table
  addPeriodRetId(name: string, startDate: Date, endDate: Date, usersId: number) {
    this.http.post<any>(`${environment.apiUrl}/addPeriodRetId`, { name, startDate, endDate, usersId })
    .subscribe(
      data => {
        this._allPeriods.next(Object.assign({}, this.allPeriodsDataStore).allPeriods);
        console.log("Data" + JSON.stringify(data))
        //Update services based on change
        this.getAllPeriods();
        this.getPeriodsWithoutBudget();
        //Return new period id
        return data;
      },
      error => console.log('Could not create budget.')
    );
  }
}
