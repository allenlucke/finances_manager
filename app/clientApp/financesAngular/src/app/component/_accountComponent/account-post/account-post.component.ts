import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { AccountService } from 'src/app/service/account.service';

@Component({
  selector: 'app-account-post',
  templateUrl: './account-post.component.html',
  styleUrls: ['./account-post.component.css']
})
export class AccountPostComponent implements OnInit {
  newAccountForm!: FormGroup
  loading = false;
  submitted = false;
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder : FormBuilder,
    private accountService : AccountService
  ) { }

  ngOnInit(){
    this.loading = false;

    //Get current user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newAccountForm = this.formBuilder.group({
      name: ['', Validators.required], 
      isCredit: ['', Validators.required], 
      creationDate: ['', Validators.required]
    });
  }

  // convenience getter for easy access to form fields
  get f() { return this.newAccountForm.controls; }

  onSubmit(){
    this.submitted = true;
  
    console.log('Pssssst')

    // stop here if form is invalid
    if (this.newAccountForm!.invalid) {
      return;
    }

    this.postAccount(
      this.f.name.value,
      this.currentUserId,
      this.f.isCredit.value,
      this.f.creationDate.value
    );

    this.newAccountForm.reset();
    this.submitted = false;
  }

  postAccount(name: string, usersId: number, isCredit: boolean, creationDate: Date){
    this.accountService.addAccountRetId(name, usersId, isCredit, creationDate )
  }

}
