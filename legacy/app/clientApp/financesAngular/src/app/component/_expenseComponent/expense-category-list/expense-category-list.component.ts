import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { ExpensesService } from 'src/app/service/expenses.service';
import { ExpenseCategory } from 'src/app/_models/expense-category';

@Component({
  selector: 'app-expense-category-list',
  templateUrl: './expense-category-list.component.html',
  styleUrls: ['./expense-category-list.component.css']
})
export class ExpenseCategoryListComponent implements OnInit {
  loading = false;
  allExpCategories! : Observable<ExpenseCategory[]>;
  error = '';
  currentUserId! : number;


  constructor(
    private expensesService : ExpensesService
  ) { }

  ngOnInit(){
    // this.loading = true;

    //All Income Categories observable
    this.allExpCategories = this.expensesService.allExpenseCats;
    this.expensesService.getAllExpenseCat();
  }

}
