import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs/operators';

import { AccountBalanceSheet } from 'src/app/_models/account-balance-sheet';
import { AccountBalanceSheetService } from 'src/app/service/account-balance-sheet.service';
import { Period } from 'src/app/_models/period';
import { Account } from 'src/app/_models/account';

@Component({
  selector: 'app-account-balance-sheet',
  templateUrl: './account-balance-sheet.component.html',
  styleUrls: ['./account-balance-sheet.component.css']
})
export class AccountBalanceSheetComponent implements OnInit {
  loading = false;
  accountBalanceSheetItems! : AccountBalanceSheet[];
  allPeriods! : Period[];
  allAccounts! : Account[];
  selectedPeriodId! : number;
  selectedAccountId! : number;
  displayedPeriod! : Period[];
  displayedAccount! : Account[];

  constructor(private balanceService: AccountBalanceSheetService) { }

  ngOnInit(){
    this.loading = true;

    this.getAllAccounts();

    this.getAllPeriods();

    this.balanceService.getCurrentPeriod().pipe(first()).subscribe(selectedPeriod => {
      this.selectedPeriodId = selectedPeriod[0].id;
      this.displayedPeriod = selectedPeriod;

      this.balanceService.getAccountById(this.allAccounts[0].id).pipe(first()).subscribe(displayedAccount => {
        this.displayedAccount = displayedAccount;

        //Load current period and first account in allAccounts array by default
        this.selectedAccountId = this.allAccounts[0].id;
        this.balanceService.getAcctBalSheet(this.selectedAccountId , this.selectedPeriodId).pipe(first()).subscribe(accountBalanceSheetItems => {
          this.accountBalanceSheetItems = accountBalanceSheetItems;
        })
      })

    })
    this.loading = false;
  }

  getAllAccounts(){
    this.balanceService.getAllAccounts().pipe(first()).subscribe(allAccounts => {
      this.allAccounts = allAccounts;
    })
  }

  getAllPeriods(){
    this.balanceService.getAllPeriods().pipe(first()).subscribe(allPeriods => {
      this.allPeriods = allPeriods;
    })
  }

  getAcctBalanceSheet() {
    this.loading = true;

    this.balanceService.getAcctBalSheet(this.selectedAccountId, this.selectedPeriodId).pipe(first()).subscribe(accountBalanceSheetItems => {
      this.loading = false;
      this.accountBalanceSheetItems = accountBalanceSheetItems;

      this.getDisplayedAccount(this.selectedAccountId);
      this.getPeriodById(this.selectedPeriodId);
    })
  }

  getCurrentPeriod() {
    this.balanceService.getCurrentPeriod().pipe(first()).subscribe(selectedPeriod => {
      this.selectedPeriodId = selectedPeriod[0].id;
      this.displayedPeriod = selectedPeriod;
    })
  }

  getDisplayedAccount(accountId: number) {
    this.balanceService.getAccountById(accountId).pipe(first()).subscribe(displayedAccount => {
      this.displayedAccount = displayedAccount;
    })
  }

  getPeriodById(periodId : number) {
    this.balanceService.getPeriodById(periodId).pipe(first()).subscribe(displayedPeriod => {
      this.displayedPeriod = displayedPeriod;
    })
  }

}
