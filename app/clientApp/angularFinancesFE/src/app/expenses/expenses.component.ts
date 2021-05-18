import { Component, OnInit } from '@angular/core';
import { RestApiService } from "../shared/rest-api.service";
import { GetExpensesHttpRequest } from '../shared/getExpensesHttpRequest';
import { Expense } from '../expense'; 

// import { EXPENSES } from '../mock-expenses';

@Component({
  selector: 'app-expenses',
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.css'],

})
export class ExpensesComponent implements OnInit {

  // expenses = EXPENSES;

    // Http Options


//   expensesRequest = {
//   expensesRequest: new GetExpensesHttpRequest ({
//     "users_id": 1,
//     "category_id": null,
//     "accounts_id": null,
//     "name": null,
//     "paid": null,
//     "recurring": null,
//     "amount_due": null,
//     "amount_paid": null,
//     "period": null,
//     "startdate" : null,
//     "enddate" : null
//   })
// }; 

  // data = data{}

  // selectedExpense?: Expense;

  constructor(
    public restApi: RestApiService
  ) { }

  ngOnInit() {
    console.log("In ng on init expenses")
    this.restApi.getExpensesSP({
      "users_id": 1,
      "category_id": null,
      "accounts_id": null,
      "name": null,
      "paid": null,
      "recurring": null,
      "amount_due": null,
      "amount_paid": null,
      "period": null,
      "startdate" : null,
      "enddate" : null
    }).subscribe((data: {}) => {
      console.log(JSON.stringify(data))
      console.log("expenses stuff " +JSON.stringify(data))
    })
    // this.loadExpenses();
    // console.log(this.loadHello)
  }

    // Get ExpensesSP
    // loadExpenses() {
    //   console.log("in load expense")
    //   return this.restApi.getExpensesSP().subscribe((data: {}) => {
    //     // this.Hello = JSON.stringify(data);
    //     console.log("expenses stuff " +JSON.stringify(data))
    //   })
    // }
  // onSelect(hero: Expense): void {
  //   this.selectedExpense = hero;
  // }
}
