import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';

import { BudgetBalanceSheet } from 'src/app/_models/budget-balance-sheet';
import { BudgetBalanceSheetService } from 'src/app/service/budget-balance-sheet.service';
import { ExpenseItem } from 'src/app/_models/expense-item';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-budget-balance-sheet',
  templateUrl: './budget-balance-sheet.component.html',
  styleUrls: ['./budget-balance-sheet.component.css'],
})
export class BudgetBalanceSheetComponent implements OnInit{
  form!: FormGroup;
  loading = false;
  balanceSheetItems! : BudgetBalanceSheet[];
  expenseItems! : ExpenseItem[];
  newHideMe : boolean[] = [];
  Index: any;
  balanceSheetItemsLength = 0;
  currentPeriodArray! : Period[];
  currentPeriodId! : number;
  availablePeriodsArray! : Period[];

  constructor(
    private balanceService: BudgetBalanceSheetService,
    private formBuilder: FormBuilder
    ) { }

  ngOnInit(){
    this.loading = true;

    this.balanceService.getCurrentPeriod().pipe(first()).subscribe(currentPeriodArray => {
      this.currentPeriodArray = currentPeriodArray
      this.currentPeriodId = currentPeriodArray[0].id;
      // console.log('Period id: ' + this.currentPeriodId);

      //Load current period by default
      this.balanceService.getBudgetBalanceSheetByPeriod(this.currentPeriodId).pipe(first()).subscribe(balanceSheetItems => {
        this.loading = false;
        this.balanceSheetItems = balanceSheetItems;
  
        this.populateNewHideMe(this.balanceSheetItemsLength);
      })
    })

    this.balanceService.getAllPeriods().pipe(first()).subscribe(availablePeriodsArray => {
      this.availablePeriodsArray = availablePeriodsArray;
    })

  }

  public getExpenseItems(i: number ){
    // console.log('In getExpenseItems()')
    this.newHideMe[i] = !this.newHideMe[i];
    this.Index = i;
    this.expenseItems = this.balanceSheetItems[i].expenseItems;
  }

  public populateNewHideMe(balanceSheetItemsLength: number){
    for (var i = 0; i < this.balanceSheetItems.length; i++) {
      console.log(this.balanceSheetItems[i]);
      if(this.newHideMe[i] != false){
        this.newHideMe.push(false);
        // console.log(this.newHideMe[i]);
      }
    }
  }

  public getSelectedPeriodBalanceSheet(event: any){
    
    this.currentPeriodId = event.target.value;

    this.balanceService.getPeriodById(this.currentPeriodId).pipe(first()).subscribe(currentPeriodArray => {
      this.currentPeriodArray = currentPeriodArray
      console.log('Period id: ' + this.currentPeriodId);
    })

    this.balanceService.getBudgetBalanceSheetByPeriod(this.currentPeriodId).pipe(first()).subscribe(balanceSheetItems => {
      this.loading = false;
      this.balanceSheetItems = balanceSheetItems;

      this.populateNewHideMe(this.balanceSheetItemsLength);
    })
  }
}
