import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { PeriodService } from 'src/app/service/period.service';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-period-list',
  templateUrl: './period-list.component.html',
  styleUrls: ['./period-list.component.css']
})
export class PeriodListComponent implements OnInit {
  loading = false;
  allPeriods! : Observable<Period[]>;
  error = '';
  currentUserId! : number;

  constructor(
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = false;

    //All periods observable
    this.allPeriods = this.periodService.allPeriods;
    this.periodService.getAllPeriods();

    //Get current user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));
  }

}
