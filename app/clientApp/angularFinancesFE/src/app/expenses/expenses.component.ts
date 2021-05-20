import { Component, OnInit } from '@angular/core';
import { RestApiService } from "../shared/rest-api.service";
import { GetExpensesHttpRequest } from '../shared/getExpensesHttpRequest';
import { Expense } from '../expense'; 


@Component({
  selector: 'app-expenses',
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.css'],

})
export class ExpensesComponent implements OnInit {

  constructor(
    public restApi: RestApiService
  ) { }

  ngOnInit() {
    console.log("In expenses component ng on init")
    this.loadExpenses();
  }

    // Get ExpensesSP
    loadExpenses() {
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
        console.log("Expenses: " +JSON.stringify(data))
      })
    }
}
