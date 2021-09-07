import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs/operators';

import { AccountBalanceSheet } from 'src/app/_models/account-balance-sheet';
import { AccountBalanceSheetService } from 'src/app/service/account-balance-sheet.service';

@Component({
  selector: 'app-account-balance-sheet',
  templateUrl: './account-balance-sheet.component.html',
  styleUrls: ['./account-balance-sheet.component.css']
})
export class AccountBalanceSheetComponent implements OnInit {
  loading = false;
  accountBalanceSheetItems!: AccountBalanceSheet[];

  constructor() { }

  ngOnInit(): void {
  }

}
