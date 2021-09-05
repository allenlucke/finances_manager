import { Component, OnInit, ViewChild, ViewChildren, QueryList, ChangeDetectorRef } from '@angular/core';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource, MatTable } from '@angular/material/table';
import { first } from 'rxjs/operators';

import { BudgetBalanceSheet } from 'src/app/_models/budget-balance-sheet';
import { BudgetBalanceSheetService } from 'src/app/service/budget-balance-sheet.service';
import { AuthenticationService } from 'src/app/service/authentication.service';

@Component({
  selector: 'app-budget-balance-sheet',
  templateUrl: './budget-balance-sheet.component.html',
  styleUrls: ['./budget-balance-sheet.component.css'],
})
export class BudgetBalanceSheetComponent {
  loading = false;
  expandedTable = false;
  balanceSheetItems!: BudgetBalanceSheet[];


  expandTable(){
    this.expandedTable = true;
  }

  constructor(private balanceService: BudgetBalanceSheetService) { }

  ngOnInit(){
    this.loading = true;

    this.balanceService.getBudgetBalanceSheetByPeriod().pipe(first()).subscribe(balanceSheetItems => {
      this.loading = false;
      this.balanceSheetItems = balanceSheetItems;
    })
  }

}
