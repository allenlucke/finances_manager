import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { retry, catchError } from 'rxjs/operators';

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
      console.log("stuff")
    return this.http.get<any>(this.apiURL + '/hello')
    .pipe(
      retry(1),
      // catchError(this.handleError)
    )
  }

  // HttpClient API get() method => 
  // getEmployees(): Observable<> {
  //   return this.http.get<>(this.apiURL + '/SP/GetIncomeSP')
  //   .pipe(
  //     retry(1),
  //     catchError(this.handleError)
  //   )
  // }


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
