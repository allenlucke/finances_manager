import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { BudgetService } from 'src/app/service/budget.service';
import { PeriodService } from 'src/app/service/period.service';
import { Budget } from 'src/app/_models/budget';
import { ExpenseCategory } from 'src/app/_models/expense-category';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-budget-post',
  templateUrl: './budget-post.component.html',
  styleUrls: ['./budget-post.component.css']
})
export class BudgetPostComponent implements OnInit {

  newBudgetForm!: FormGroup;
  loading = false;
  submitted = false;
  allBudgets! : Budget[];
  periodsSansBudget! : Period[];
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder : FormBuilder,
    private budgetService : BudgetService,
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = true;

    this.getPeriodsWithoutBudget();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newBudgetForm = this.formBuilder.group({
      name: ['', Validators.required],
      periodId: ['', Validators.required]
    });
  }

    // convenience getter for easy access to form fields
    get f() { return this.newBudgetForm.controls; }

    onSubmit(){
      this.submitted = true;
  
      // stop here if form is invalid
      if (this.newBudgetForm!.invalid) {
        return;
      }
  
      this.postBudget(this.f.name.value, this.f.periodId.value, this.currentUserId);
  
      this.getAllBudgets();
      this.newBudgetForm.reset();
  
      this.submitted = false;
    }

    getAllBudgets() {
      this.budgetService.getAllBudgets().pipe(first()).subscribe(allBudgets => {
        this.allBudgets = allBudgets;
        this.loading = false;
      })
    }

    getPeriodsWithoutBudget() {
      this.periodService.getPeriodsWithoutBudget().pipe(first()).subscribe(periodsSansBudget => {
        this.periodsSansBudget = periodsSansBudget;
        this.loading = false;
      })
    }

    postBudget(name: string, periodId: number, usersId: number){
      this.budgetService.addBudgetRetId(name, periodId, usersId).pipe(first()).subscribe(returnedId => {
        this.returnedId = returnedId;
      })
      this.getAllBudgets();
    }
}
