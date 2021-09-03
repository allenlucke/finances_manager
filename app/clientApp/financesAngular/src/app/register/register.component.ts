import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';

import { AuthenticationService } from 'src/app/service/authentication.service';

@Component({
  selector: 'app-register',
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css']
})
export class RegisterComponent implements OnInit {
  registerForm!: FormGroup;
  loading = false;
  submitted = false;
  returnUrl!: string;
  error = '';

  constructor(
    private formBuilder: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private authenticationService: AuthenticationService
  ) {
      // redirect to home if already logged in
        if (this.authenticationService.currentUserValue) { 
          this.router.navigate(['/']);
      }
   }


   ngOnInit() {
    this.registerForm = this.formBuilder.group({
        firstName: ['', Validators.required],
        lastName: ['', Validators.required],
        userName: ['', Validators.required],
        password: ['', Validators.required],
        securityLevel: 1,
        email: ['', Validators.required],
        role: 'user'
    });

    // get return url from route parameters or default to '/'
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';
}

    // convenience getter for easy access to form fields
    get f() { return this.registerForm.controls; }

    onSubmit() {
      this.submitted = true;

      // stop here if form is invalid
      if (this.registerForm.invalid) {
          return;
      }

      this.loading = true;
      this.authenticationService.register( this.f.firstName.value, 
        this.f.lastName.value, this.f.userName.value, this.f.password.value, 
        this.f.securityLevel.value, this.f.email.value, this.f.role.value)
          .pipe(first())
          .subscribe(
            (                data: any) => {
                  this.router.navigate([this.returnUrl]);
              },
            (                error: string) => {
                  this.error = error;
                  this.loading = false;
              });
    }
}
