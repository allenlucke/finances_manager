import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs/operators';

import { BudgetBalanceSheet } from 'src/app/_models/budget-balance-sheet';
import { BudgetBalanceSheetService } from 'src/app/service/budget-balance-sheet.service';
import { ExpenseItem } from 'src/app/_models/expense-item';

@Component({
  selector: 'app-test-bal-sheet',
  templateUrl: './test-bal-sheet.component.html',
  styleUrls: ['./test-bal-sheet.component.css']
})
export class TestBalSheetComponent implements OnInit {
  loading = false;
  expandedTable = false;
  balanceSheetItems!: BudgetBalanceSheet[];
  expenseItems!: ExpenseItem[];
  hideme = true;
  Index: any;

  constructor(private balanceService: BudgetBalanceSheetService) { }

  ngOnInit() {

    this.loading = true;

    this.balanceService.getBudgetBalanceSheetByPeriod().pipe(first()).subscribe(balanceSheetItems => {
      this.loading = false;
      this.balanceSheetItems = balanceSheetItems;
    })

  }

  public getExpenseItems(balSheetItem: BudgetBalanceSheet){
    console.log('In getExpenseItems()')
    this.hideme = !this.hideme;
    this.expenseItems = balSheetItem.expenseItems;
  }

  // public getSubList(index: number){
  //   this.hideme = !this.hideme;
  //   this.Index = index;
  // }
}
