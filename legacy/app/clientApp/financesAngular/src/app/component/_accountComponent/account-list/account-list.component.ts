import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountService } from 'src/app/service/account.service';
import { Account } from 'src/app/_models/account';

@Component({
  selector: 'app-account-list',
  templateUrl: './account-list.component.html',
  styleUrls: ['./account-list.component.css']
})
export class AccountListComponent implements OnInit {
  loading = false;
  allAccounts! : Observable<Account[]>;
  error = '';
  currentUserId! : number;

  constructor(
    private accountService : AccountService
  ) { }

  ngOnInit(){
    this.loading = false;

    //All accounts observable
    this.allAccounts = this.accountService.allAccounts;
    this.accountService.getAllAccounts();
    
  }

}
