import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { first } from 'rxjs/operators';
import { BudgetService } from 'src/app/service/budget.service';
import { PeriodService } from 'src/app/service/period.service';
import { Budget } from 'src/app/_models/budget';
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
  periodsSansBudget! : Observable<Period[]>;
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder : FormBuilder,
    private budgetService : BudgetService,
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = false;

    //Period sans budget observable
    this.periodsSansBudget = this.periodService.periodsWithoutBudget;
    this.periodService.getPeriodsWithoutBudget();

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
  
      console.log("in submit budget")
      this.postBudget(this.f.name.value, this.f.periodId.value, this.currentUserId);
  
      this.newBudgetForm.reset(); 
      this.submitted = false;
    }

    postBudget(name: string, periodId: number, usersId: number){
      this.budgetService.addBudgetRetId(name, periodId, usersId);
      
    }
}
