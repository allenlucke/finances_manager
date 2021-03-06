import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { BudgetService } from 'src/app/service/budget.service';
import { Budget } from 'src/app/_models/budget';

@Component({
  selector: 'app-budget-list',
  templateUrl: './budget-list.component.html',
  styleUrls: ['./budget-list.component.css']
})
export class BudgetListComponent implements OnInit {
  loading = true;
  error = '';
  currentUserId! : number;
  allBudgets! : Observable<Budget[]>;

  constructor(
    private budgetService : BudgetService
  ) { }

  ngOnInit(){
    this.loading = false;

    //All budgets observable
    this.allBudgets = this.budgetService.allBudgets;
    this.budgetService.getAllBudgets();

    //Get current user
    this.currentUserId = Number(localStorage.getItem('currentUserId'));
    
  }

}
