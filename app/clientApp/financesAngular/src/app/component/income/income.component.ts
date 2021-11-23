import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { first } from 'rxjs/operators';
import { AccountService } from 'src/app/service/account.service';
import { IncomeService } from 'src/app/service/income.service';
import { Account } from 'src/app/_models/account';
import { BudgetIncomeCategoryWithName } from 'src/app/_models/budget-income-category-with-name';
import { IncomeCategory } from 'src/app/_models/income-category';

@Component({
  selector: 'app-income',
  templateUrl: './income.component.html',
  styleUrls: ['./income.component.css']
})
export class IncomeComponent implements OnInit {
  newCategoryForm!: FormGroup;
  newItemForm!: FormGroup;
  loading = false;
  submitted = false;
  submittedItem = false;
  error = '';
  errorIncItem = '';
  allIncomeCategories! : Observable<IncomeCategory[]>;
  allAccounts! : Account[]; 
  currentUserId! : number;
  returnedId! : number;
  postItemAvailableBudgIncCats! : BudgetIncomeCategoryWithName[];

  constructor(
    private formBuilder: FormBuilder,
    private incomeService: IncomeService,
    private accountService : AccountService
  ) { }

  ngOnInit() {

    this.loading = true;

    //All Income Categories observable
    this.allIncomeCategories = this.incomeService.allIncomeCats;
    this.incomeService.getAllIncomeCat();

    this.getAllAccounts();

    //Get user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newCategoryForm = this.formBuilder.group({
      name: ['', Validators.required],
    });

    this.newItemForm = this.formBuilder.group({
      budgetIncomeCategoryId: ['', Validators.required],
      name: ['', Validators.required],
      receivedDate: ['', Validators.required],
      amount: ['', Validators.required],
      // amountExpected: ['', Validators.required],
      // amountReceived: ['', Validators.required],
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

    this.postIncomeCategory(this.f.name.value)
    //TODO
    // this.getAllIncomeCategories();
    this.newCategoryForm.reset();
    this.f.name.untouched;

    this.submitted = false;
  }

  onSubmitIncItem() {
    this.submittedItem = true;

    // stop here if form is invalid
    if (this.newItemForm!.invalid) {
      return;
    }

    this.postIncomeItem(
      this.iF.budgetIncomeCategoryId.value, 
      this.iF.name.value, 
      this.iF.receivedDate.value, 
      this.iF.amount.value, 
      this.iF.amount.value,
      this.iF.accountId.value, 
      this.currentUserId )

      this.newItemForm.reset();
      this.iF.untouched;
      this.submittedItem = false;
  }

  onDateInputChange(): void {
    this.newItemForm.get('receivedDate')?.valueChanges.subscribe(val => {
      console.log('Tracking change to date. Date: ' + val);
      this.getBudgetIncCatsWithNameByDate(val);
    });
  }

  postIncomeCategory(name: string){
    this.incomeService.addIncomeCatRetId(name, this.currentUserId)
  }

  getBudgetIncCatsWithNameByDate(date: Date) {
    this.incomeService.getBudgetIncCatsWithNameByDate(date).pipe(first()).subscribe(postItemAvailableBudgIncCats => {
      this.postItemAvailableBudgIncCats = postItemAvailableBudgIncCats;
      // console.log(postItemAvailableBudgExpCats)
    })
  }

  getAllAccounts(){
    this.accountService.getAllAccounts().pipe(first()).subscribe(allAccounts => {
      this.allAccounts = allAccounts;
      this.loading = false;
    })
  }

  postIncomeItem(
    budgetIncomeCategoryId: number,
    name: string,
    receivedDate: string,
    amountExpected: number,
    amountReceived: number,
    accountId: number,
    usersId: number

    ){
    this.incomeService.addIncItemRetId(
      budgetIncomeCategoryId, 
      name, 
      receivedDate, 
      amountExpected, 
      amountReceived,
      accountId, 
      usersId 

      ).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
    })
  }

}
