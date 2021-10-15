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

  newPeriodForm!: FormGroup;
  loading = false;
  submitted = false;
  allPeriods! : Period[];
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder: FormBuilder,
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = true;

    this.getAllPeriods();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newPeriodForm = this.formBuilder.group({
      name: ['', Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required]
  });
  }

  // convenience getter for easy access to form fields
  get f() { return this.newPeriodForm.controls; }

  getAllPeriods() {
    this.periodService.getAllPeriods().pipe(first()).subscribe(allPeriods => {
      this.allPeriods = allPeriods;
      this.loading = false;
    })
  }

  onSubmit(){
    this.submitted = true;

      // stop here if form is invalid
      if (this.newPeriodForm!.invalid) {
        return;
    }

    this.postPeriod(this.f.name.value, this.f.startDate.value, this.f.endDate.value, this.currentUserId);

    this.getAllPeriods();
    this.newPeriodForm.reset();

    this.submitted = false;
  }

  postPeriod(name: string, startDate: Date, endDate: Date, usersId: number){
    this.periodService.addPeriodRetId(name, startDate, endDate, usersId).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
    })
    this.getAllPeriods();
  }

}
