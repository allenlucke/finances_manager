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

  addPeriodRetId(period: Period) {
    return this.http.post<Period>(`${environment.apiUrl}/addPeriodRetId`, { period })
  }
}
