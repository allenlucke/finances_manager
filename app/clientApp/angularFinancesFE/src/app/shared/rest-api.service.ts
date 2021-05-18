import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { retry, catchError } from 'rxjs/operators';
import { GetExpensesHttpRequest } from '../shared/getExpensesHttpRequest';
// import { getIncomeHttpRequest } from '../shared/getIncomeHttpRequest';
import { Expense } from '../expense';

@Injectable({
  providedIn: 'root'
})
export class RestApiService {

   // Define API
   apiURL = 'http://localhost:8080/Finances';

  constructor(private http: HttpClient) { }

    /*========================================
    CRUD Methods for consuming RESTful API
  =========================================*/

  // Http Options
  httpOptions = {
    headers: new HttpHeaders({
      'Content-Type': 'application/json'
    })
  } 

  // HttpClient API get() method => SayHello Test Method
    getHello(): Observable<any> {
      console.log("In get hello API call")
    return this.http.get<any>(this.apiURL + '/hello')
    .pipe(
      retry(1),
      // catchError(this.handleError)
    )
  }

  // HttpClient API get() method => Get Expenses call to stored procedure
  getExpensesSP(data: any): Observable<any> {
    // const headers = { 'content-type': 'application/json'} 
    const reqBody = JSON.stringify(data)
    console.log(reqBody);
    return this.http.post<any>(this.apiURL + '/SP/GetExpensesSP', reqBody)
    // return this.http.post<any>(this.apiURL + '/SP/GetExpensesSP', reqBody, {'headers' : headers})
    .pipe(
      retry(1),
      catchError(this.handleError)
    )
  }


  // Error handling 
  handleError(error: any) {
    let errorMessage = '';
    if(error.error instanceof ErrorEvent) {
      // Get client-side error
      errorMessage = error.error.message;
    } else {
      // Get server-side error
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    window.alert(errorMessage);
    return throwError(errorMessage);
  }
}