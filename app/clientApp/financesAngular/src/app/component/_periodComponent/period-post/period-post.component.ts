import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { first } from 'rxjs/operators';
import { PeriodService } from 'src/app/service/period.service';
import { Period } from 'src/app/_models/period';

@Component({
  selector: 'app-period-post',
  templateUrl: './period-post.component.html',
  styleUrls: ['./period-post.component.css']
})
export class PeriodPostComponent implements OnInit {
  newPeriodForm!: FormGroup;
  loading = false;
  submitted = false;
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder : FormBuilder,
    private periodService : PeriodService
  ) { }

  ngOnInit(){
    this.loading = false;

    //Get current user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newPeriodForm = this.formBuilder.group({
      name: ['', Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required]
    });
  }

    // convenience getter for easy access to form fields
    get f() { return this.newPeriodForm.controls; }

    onSubmit(){
      this.submitted = true;
  
        // stop here if form is invalid
        if (this.newPeriodForm!.invalid) {
          return;
      }
  
      this.postPeriod(this.f.name.value, this.f.startDate.value, this.f.endDate.value, this.currentUserId);
  
      this.newPeriodForm.reset();
      this.submitted = false;
    }
  
    postPeriod(name: string, startDate: Date, endDate: Date, usersId: number){
      this.periodService.addPeriodRetId(name, startDate, endDate, usersId)
    }
}
