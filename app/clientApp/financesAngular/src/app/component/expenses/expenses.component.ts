import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs/operators';
import { ExpensesService } from 'src/app/service/expenses.service';
import { ExpenseCategory } from 'src/app/_models/expense-category';

@Component({
  selector: 'app-expenses',
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.css']
})
export class ExpensesComponent implements OnInit {
  loading = false;
  allExpCategories! : ExpenseCategory[]; 

  constructor(private expensesService : ExpensesService) { }

  ngOnInit(){
    this.loading = true;

    this.getAllExpenseCategories();
    // this.expensesService.getAllExpCat().pipe(first()).subscribe(allExpCategories => {
    //   this.allExpCategories = allExpCategories;
    // })


    this.loading = false;
  }

  getAllExpenseCategories(){
    this.expensesService.getAllExpCat().pipe(first()).subscribe(allExpCategories => {
      this.allExpCategories = allExpCategories;
  })
  }

}
