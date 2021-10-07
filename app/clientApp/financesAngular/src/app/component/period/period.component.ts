import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { PeriodService } from 'src/app/service/period.service';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-period',
  templateUrl: './period.component.html',
  styleUrls: ['./period.component.css']
})
export class PeriodComponent implements OnInit {

  loading = false;
  allPeriods! : Period[];
  currentUserId! : number;

  constructor(
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = true;

    this.getAllPeriods();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

  }

  getAllPeriods() {
    this.periodService.getAllPeriods().pipe(first()).subscribe(allPeriods => {
      this.allPeriods = allPeriods;
      this.loading = false;
    })
  }

}
