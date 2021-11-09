import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
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
  allBudgets! : Budget[];
  periodsSansBudget! : Period[];
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

    this.getAllBudgets();
    this.getPeriodsWithoutBudget();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

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
  
      this.getAllBudgets();
      this.cloneBudgetForm.reset();
  
      this.submittedClone = false;
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

    cloneBudget(templateBudgetId : number, name: string, periodId: number, usersId: number){
      this.budgetService.cloneBudget(templateBudgetId, name, periodId, usersId).pipe(first()).subscribe(returnedId => {
        this.returnedId = returnedId;
      })
      this.getAllBudgets();
    }


    
}
