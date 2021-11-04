import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { AccountService } from 'src/app/service/account.service';
import { ExpensesService } from 'src/app/service/expenses.service';
import { Account } from 'src/app/_models/account';
import { BudgetExpenseCategory } from 'src/app/_models/budget-expense-category';
import { BudgetExpenseCategoryWithName } from 'src/app/_models/budget-expense-category-with-name';
import { ExpenseCategory } from 'src/app/_models/expense-category';

@Component({
  selector: 'app-expenses',
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.css']
})
export class ExpensesComponent implements OnInit {
  newCategoryForm!: FormGroup;
  newItemForm!: FormGroup;
  loading = false;
  submitted = false;
  submittedItem = false;
  allExpCategories! : ExpenseCategory[];
  allAccounts! : Account[]; 
  error = '';
  errorExpItem = '';
  currentUserId! : number;
  returnedId! : number;
  postItemAvailableBudgExpCats! : BudgetExpenseCategoryWithName[];

  constructor(
    private formBuilder: FormBuilder,
    private expensesService : ExpensesService,
    private accountService : AccountService
    ) { }

  ngOnInit(){
    this.loading = true;

    this.getAllExpenseCategories();

    this.getAllAccounts();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newCategoryForm = this.formBuilder.group({
      name: ['', Validators.required],
    });

    this.newItemForm = this.formBuilder.group({
      budgetExpenseCategoryId: ['', Validators.required],
      name: ['', Validators.required],
      transactionDate: ['', Validators.required],
      amount: ['', Validators.required],
      // paidWithCredit: ['', Validators.required],
      // paymentToCreditAccount: ['', Validators.required],
      // interestPaymentToCreditAccount: ['', Validators.required],
      accountId: ['', Validators.required]
    });

    this.onDateInputChange();
  }

  // convenience getter for easy access to form fields
  get f() { return this.newCategoryForm.controls; }
  get iF() { return this.newItemForm.controls; }

  onSubmit() {
    this.submitted = true;

    // stop here if form is invalid
    if (this.newCategoryForm!.invalid) {
        return;
    }

    this.postExpenseCategory(this.f.name.value)

    this.getAllExpenseCategories();
    this.newCategoryForm.reset();
    this.f.name.untouched;

    this.submitted = false;
  }

  onSubmitExpItem() {
    this.submittedItem = true;

    // stop here if form is invalid
    if (this.newItemForm!.invalid) {
      return;
    }

    this.postExpenseItem(
      this.iF.budgetExpenseCategoryId.value, 
      this.iF.name.value, 
      this.iF.transactionDate.value, 
      this.iF.amount.value, 
      false,
      false,
      // this.iF.paymentToCreditAccount.value, 
      // this.iF.interestPaymentToCreditAccount.value, 
      this.iF.accountId.value, 
      this.currentUserId)

      this.newItemForm.reset();
      this.iF.untouched;
      this.submittedItem = false;
  }

  onDateInputChange(): void {
  
    this.newItemForm.get('transactionDate')?.valueChanges.subscribe(val => {
      console.log('Tracking change to date. Date: ' + val);
      this.getBudgetExpCatsWithNameByDate(val);
      console.log(JSON.stringify(this.postItemAvailableBudgExpCats));

    });
  }


  getAllExpenseCategories(){
    this.expensesService.getAllExpCat().pipe(first()).subscribe(allExpCategories => {
      this.allExpCategories = allExpCategories;
      this.loading = false;
    })
  }

  postExpenseCategory(name: string){
    this.expensesService.addExpCatRetId(name, this.currentUserId).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
      })
      this.getAllExpenseCategories();
  }

  getBudgetExpCatsWithNameByDate(date: Date) {
    this.expensesService.getBudgetExpCatsWithNameByDate(date).pipe(first()).subscribe(postItemAvailableBudgExpCats => {
      this.postItemAvailableBudgExpCats = postItemAvailableBudgExpCats;
      // console.log(postItemAvailableBudgExpCats)
    })
  }

  getAllAccounts(){
    this.accountService.getAllAccounts().pipe(first()).subscribe(allAccounts => {
      this.allAccounts = allAccounts;
      this.loading = false;
    })
  }

  postExpenseItem(

    budgetExpenseCategoryId: number, 
    name: string, 
    transactionDate: Date, 
    amount: number, 
    paymentToCreditAccount: boolean, 
    interestPaymentToCreditAccount: boolean, 
    accountId: number, 
    usersId: number

    ){
    this.expensesService.addExpItemRetId(

      budgetExpenseCategoryId, 
      name, 
      transactionDate, 
      amount, 
      paymentToCreditAccount, 
      interestPaymentToCreditAccount,
      accountId, 
      usersId

      ).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
    })
  }

}
