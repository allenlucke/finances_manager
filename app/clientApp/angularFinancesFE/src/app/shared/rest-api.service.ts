import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { retry, catchError } from 'rxjs/operators';
import { GetExpensesHttpRequest } from '../shared/getExpensesHttpRequest';
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
      catchError(this.handleError)
    )
  }

  // HttpClient API get() method => Get Expenses call to stored procedure
  // getExpensesSP(data: any): Observable<any> {
  //   console.log("In get expenses API call")
  //   const headers= new HttpHeaders()
  //     .set('content-type', 'application/json')
  //     .set('Access-Control-Allow-Origin', '*');
  //   const reqBody = JSON.stringify(data)
  //   console.log("getExpensesSP Request Body: "+ reqBody);
  //   return this.http.post<any>(this.apiURL + '/SP/GetExpensesSP', reqBody, { 'headers': headers })
  //   .pipe(
  //     retry(1),
  //     catchError(this.handleError)
  //   )
  // }
  getExpensesSP(data: GetExpensesHttpRequest): Observable<any> {
    console.log("In get expenses API call")
    const headers= new HttpHeaders()
      .set('content-type', 'application/json')
      .set('Access-Control-Allow-Origin', '*');
    const reqBody = JSON.stringify(data)
    console.log("getExpensesSP Request Body: "+ reqBody);
    return this.http.post<any>(this.apiURL + '/SP/GetExpensesSP', reqBody, { 'headers': headers })
    .pipe(
      retry(1),
      catchError(this.handleError)
    )
  }


  //INPUT DATA
  //The following GET requests are used to supply data for input elements
  getPeriodData(data: any): Observable<any> {
      console.log("In get period data API call")
    const headers= new HttpHeaders()
      .set('content-type', 'application/json')
      .set('Access-Control-Allow-Origin', '*');
    return this.http.get<any>(this.apiURL + '/InputPopData/PeriodData/?id=' + data.id, { 'headers': headers })
    .pipe(
      retry(1),
      catchError(this.handleError)
    )
  }
  getExpensesCategoriesData(data: any): Observable<any> {
    console.log("In get expenses categories api call")
    const headers= new HttpHeaders()
      .set('content-type', 'application/json')
      .set('Access-Control-Allow-Origin', '*');
      return this.http.get<any>(this.apiURL + '/InputPopData/ExpenseCatData/?id=' + data.id, { 'headers': headers })
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
