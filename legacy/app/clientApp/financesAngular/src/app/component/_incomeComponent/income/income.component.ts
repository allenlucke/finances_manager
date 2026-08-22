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

  constructor( ) { }

  ngOnInit() { }

}
