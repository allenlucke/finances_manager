import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { BudgetService } from 'src/app/service/budget.service';
import { PeriodService } from 'src/app/service/period.service';
import { Budget } from 'src/app/_models/budget';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-budget',
  templateUrl: './budget.component.html',
  styleUrls: ['./budget.component.css']
})
export class BudgetComponent implements OnInit {

  newBudgetForm!: FormGroup;
  loading = false;
  submitted = false;
  allBudgets! : Budget[];
  allPeriods! : Period[];
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

    this.getAllBudgets();
    this.getAllPeriods();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newBudgetForm = this.formBuilder.group({
      name: ['', Validators.required],
      periodId: ['', Validators.required]
  });
  }

  // convenience getter for easy access to form fields
  get f() { return this.newBudgetForm.controls; }

  getAllBudgets() {
    this.budgetService.getAllBudgets().pipe(first()).subscribe(allBudgets => {
      this.allBudgets = allBudgets;
      this.loading = false;
      console.log(allBudgets);
    })
  }

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

  postBudget(name: string, periodId: number, usersId: number){
    this.budgetService.addBudgetRetId(name, periodId, usersId).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
    })
    this.getAllBudgets();
  }

  getAllPeriods() {
    this.periodService.getAllPeriods().pipe(first()).subscribe(allPeriods => {
      this.allPeriods = allPeriods;
      this.loading = false;
    })
  }

}
