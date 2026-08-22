import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { IncomeService } from 'src/app/service/income.service';
import { IncomeCategory } from 'src/app/_models/income-category';

@Component({
  selector: 'app-income-category-list',
  templateUrl: './income-category-list.component.html',
  styleUrls: ['./income-category-list.component.css']
})
export class IncomeCategoryListComponent implements OnInit {
  loading = false;
  submitted = false;
  error = '';
  allIncomeCategories! : Observable<IncomeCategory[]>;
  currentUserId! : number;

  constructor(
    private incomeService: IncomeService
  ) { }

  ngOnInit(){
    // this.loading = true;

    //All Income Categories observable
    this.allIncomeCategories = this.incomeService.allIncomeCats;
    this.incomeService.getAllIncomeCat();

    //Get user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));
  }

}
