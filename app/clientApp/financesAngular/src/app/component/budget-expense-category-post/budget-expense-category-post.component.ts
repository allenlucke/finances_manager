import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { BudgetService } from 'src/app/service/budget.service';
import { Budget } from 'src/app/_models/budget';
import { ExpenseCategory } from 'src/app/_models/expense-category';

@Component({
  selector: 'app-budget-expense-category-post',
  templateUrl: './budget-expense-category-post.component.html',
  styleUrls: ['./budget-expense-category-post.component.css']
})
export class BudgetExpenseCategoryPostComponent implements OnInit {

  addBudgExpCatForm!: FormGroup;
  loading = false;
  submittedBudgExpCat = false;
  allBudgets! : Budget[];
  unassignedExpenseCats! : ExpenseCategory[];
  errorBudgExpCat = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder : FormBuilder,
    private budgetService : BudgetService
  ) { }

  ngOnInit() {
    this.loading = true;
    this.getAllBudgets();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.addBudgExpCatForm = this.formBuilder.group({
      budgetId: ['', Validators.required],
      expenseCategoryId: ['', Validators.required],
      amountBudgeted: ['', Validators.required]
    })

    this.onBudgetInputChange();
  }

    // convenience getter for easy access to form fields
    get bECF() { return this.addBudgExpCatForm.controls; }

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

    onBudgetInputChange(): void {
      this.addBudgExpCatForm.get('budgetId')?.valueChanges.subscribe(val => {
        console.log('Tracking change to budget id. budgetId: ' + val);
        this.getExpenseCatsNotAssignedToBudget(val);
      });
    }

    getAllBudgets() {
      this.budgetService.getAllBudgets().pipe(first()).subscribe(allBudgets => {
        this.allBudgets = allBudgets;
        this.loading = false;
      })
    }

      //Add expense category to an existing budget
    addBudgetExpenseCategory(budgetId : number, expenseCategoryId : number, amountBudgeted : number, usersId : number){
      this.budgetService.addBudgetExpenseCategory(budgetId, expenseCategoryId, amountBudgeted, usersId).pipe(first()).subscribe(returnedId => {
        this.returnedId = returnedId;
        this.getAllBudgets;
      })
    }

    //Get expense categories not currently assigned to selected budget
    getExpenseCatsNotAssignedToBudget(budgetId : number){
      console.log(budgetId);
      this.budgetService.getExpenseCatsNotAssignedToBudget(budgetId).pipe(first()).subscribe(unassignedExpenseCats => {
        this.unassignedExpenseCats = unassignedExpenseCats;
      })
    }
}
