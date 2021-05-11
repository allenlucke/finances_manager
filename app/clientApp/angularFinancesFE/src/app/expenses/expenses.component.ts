import { Component, OnInit } from '@angular/core';
import { Expense } from '../expense'; 

@Component({
  selector: 'app-expenses',
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.css'],

})
export class ExpensesComponent implements OnInit {

  expense: Expense = {
    id: 1, 
    name: 'marexpense1', 
    paid: false, 
    due_by: 'mar-01-2021', 
    paid_on: '', 
    recurring: false, 
    amount_due: 100.6, 
    amount_paid: 0, 
    users_id: 1, 
    category_id: 1, 
    accounts_id: 0
  };
  constructor() { }

  ngOnInit(): void {
  }

}
