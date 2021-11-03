import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { BudgetService } from 'src/app/service/budget.service';
import { PeriodService } from 'src/app/service/period.service';
import { Budget } from 'src/app/_models/budget';
import { BudgetExpenseCategory } from 'src/app/_models/budget-expense-category';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-budget',
  templateUrl: './budget.component.html',
  styleUrls: ['./budget.component.css']
})
export class BudgetComponent implements OnInit {

  newBudgetForm!: FormGroup;
  cloneBudgetForm!: FormGroup;
  addBudgExpCatForm!: FormGroup;
  loading = false;
  submitted = false;
  submittedClone = false;
  submittedBudgExpCat = false;
  allBudgets! : Budget[];
  allPeriods! : Period[];
  error = '';
  errorClone = '';
  errorBudgExpCat = '';
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

    this.cloneBudgetForm = this.formBuilder.group({
      name: ['', Validators.required],
      periodId: ['', Validators.required],
      templateBudgetId: ['', Validators.required]
    });

    this.addBudgExpCatForm = this.formBuilder.group({
      budgetId: ['', Validators.required],
      expenseCategoryId: ['', Validators.required],
      amountBudgeted: ['', Validators.required]
    })
  }

  // convenience getter for easy access to form fields
  get f() { return this.newBudgetForm.controls; }
  get cF() { return this.cloneBudgetForm.controls; }
  get bECF() { return this.addBudgExpCatForm.controls; }

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

  onSubmitAddBudgExpCat(){
    this.submittedBudgExpCat = true;

    // stop here if form is invalid
    if (this.addBudgExpCatForm!.invalid) {
      return;
    }

    this.addBudgetExpenseCategory(this.bECF.budgetId.value, this.bECF.expenseCategoryId.value, this.bECF.amountBudgeted.value, this.currentUserId);

    this.addBudgExpCatForm.reset();

    this.submittedBudgExpCat = false;
  }


  getAllBudgets() {
    this.budgetService.getAllBudgets().pipe(first()).subscribe(allBudgets => {
      this.allBudgets = allBudgets;
      this.loading = false;
    })
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

  cloneBudget(templateBudgetId : number, name: string, periodId: number, usersId: number){
    this.budgetService.cloneBudget(templateBudgetId, name, periodId, usersId).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
    })
    this.getAllBudgets();
  }

  //Add expense category to an existing budget
  addBudgetExpenseCategory(budgetId : number, expenseCategoryId : number, amountBudgeted : number, usersId : number){
    this.budgetService.addBudgetExpenseCategory(budgetId, expenseCategoryId, amountBudgeted, usersId).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
    })
  }

}
