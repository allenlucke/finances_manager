import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { first } from 'rxjs/operators';
import { AccountService } from 'src/app/service/account.service';
import { IncomeService } from 'src/app/service/income.service';
import { Account } from 'src/app/_models/account';
import { BudgetIncomeCategoryWithName } from 'src/app/_models/budget-income-category-with-name';

@Component({
  selector: 'app-income-item-post',
  templateUrl: './income-item-post.component.html',
  styleUrls: ['./income-item-post.component.css']
})
export class IncomeItemPostComponent implements OnInit {
  newItemForm!: FormGroup;
  loading = false;
  submitted = false;
  submittedItem = false;
  error = '';
  errorIncItem = '';
  allAccounts! : Observable<Account[]>;  
  currentUserId! : number;
  returnedId! : number;
  postItemAvailableBudgIncCats! : BudgetIncomeCategoryWithName[];
  
  constructor(
    private formBuilder: FormBuilder,
    private incomeService: IncomeService,
    private accountService : AccountService
  ) { }

  ngOnInit() {

    // this.loading = true;

    //All accounts observable
    this.allAccounts = this.accountService.allAccounts;
    this.accountService.getAllAccounts();

    //Get user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));

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
  get iF() { return this.newItemForm.controls; }

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

  getBudgetIncCatsWithNameByDate(date: Date) {
    this.incomeService.getBudgetIncCatsWithNameByDate(date).pipe(first()).subscribe(postItemAvailableBudgIncCats => {
      this.postItemAvailableBudgIncCats = postItemAvailableBudgIncCats;
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
