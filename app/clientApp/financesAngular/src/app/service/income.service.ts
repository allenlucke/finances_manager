import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

import { environment } from 'src/environments/environment';
import { IncomeCategory } from '../_models/income-category';

@Injectable({
  providedIn: 'root'
})
export class IncomeService {

  constructor(private http: HttpClient) { }

  getAllIncomeCat() {
    return this.http.get<IncomeCategory[]>(`${environment.apiUrl}/getAllIncomeCats`);
  }

  addIncomeCatRetId(name: string, usersId: number ) {
    return this.http.post<any>(`${environment.apiUrl}/addIncomeCatReturnId`, { name, usersId })
  }

}
