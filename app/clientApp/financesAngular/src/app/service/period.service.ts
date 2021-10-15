import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from 'src/environments/environment';
import { Period } from '../_models/period';

@Injectable({
  providedIn: 'root'
})
export class PeriodService {

  constructor(private http: HttpClient) { }

  getAllPeriods() {
    return this.http.get<Period[]>(`${environment.apiUrl}/getAllPeriods`);
  }

  addPeriodRetId(name: string, startDate: Date, endDate: Date, usersId: number) {
    return this.http.post<any>(`${environment.apiUrl}/addPeriodRetId`, { name, startDate, endDate, usersId })
  }
}
