import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { BudgetService } from 'src/app/service/budget.service';
import { PeriodService } from 'src/app/service/period.service';
import { Budget } from 'src/app/_models/budget';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-budget-clone',
  templateUrl: './budget-clone.component.html',
  styleUrls: ['./budget-clone.component.css']
})
export class BudgetCloneComponent implements OnInit {

  cloneBudgetForm!: FormGroup;
  loading = false;
  submittedClone = false;
  allBudgets! : Observable<Budget[]>;
  periodsSansBudget! : Observable<Period[]>;
  errorClone = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder : FormBuilder,
    private budgetService : BudgetService,
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = true;

    //All budgets observable
    this.allBudgets = this.budgetService.allBudgets;
    this.budgetService.getAllBudgets();

    //Period sans budget observable
    this.periodsSansBudget = this.periodService.periodsWithoutBudget;
    this.periodService.getPeriodsWithoutBudget();

    //Get user id from storage
    // this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.cloneBudgetForm = this.formBuilder.group({
      name: ['', Validators.required],
      periodId: ['', Validators.required],
      templateBudgetId: ['', Validators.required]
    });
    this.loading = false;
  }

    // convenience getter for easy access to form fields
    get cF() { return this.cloneBudgetForm.controls; }

    onSubmitClone(){
      this.submittedClone = true;
  
      // stop here if form is invalid
      if (this.cloneBudgetForm!.invalid) {
        return;
      }
  
      this.cloneBudget(this.cF.templateBudgetId.value ,this.cF.name.value, this.cF.periodId.value, this.currentUserId);

      this.cloneBudgetForm.reset(); 
      this.submittedClone = false;
    }

    cloneBudget(templateBudgetId : number, name: string, periodId: number, usersId: number){
      this.budgetService.cloneBudget(templateBudgetId, name, periodId, usersId)
    }
    
}