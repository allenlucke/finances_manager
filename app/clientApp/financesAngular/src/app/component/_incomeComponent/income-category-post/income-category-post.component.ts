import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { IncomeService } from 'src/app/service/income.service';


@Component({
  selector: 'app-income-category-post',
  templateUrl: './income-category-post.component.html',
  styleUrls: ['./income-category-post.component.css']
})
export class IncomeCategoryPostComponent implements OnInit {
  newCategoryForm!: FormGroup;
  loading = false;
  submitted = false;
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder: FormBuilder,
    private incomeService: IncomeService
  ) { }

  ngOnInit(){
    // this.loading = true;

    //Get user id
    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    this.newCategoryForm = this.formBuilder.group({
      name: ['', Validators.required],
    });
  }

  // convenience getter for easy access to form fields
  get f() { return this.newCategoryForm.controls; }

  onSubmit() {
    this.submitted = true;
    // stop here if form is invalid
    if (this.newCategoryForm!.invalid) {
        return;
    }

    this.postIncomeCategory(this.f.name.value)
    //TODO
    // this.getAllIncomeCategories();
    this.newCategoryForm.reset();
    this.f.name.untouched;

    this.submitted = false;
  }

  postIncomeCategory(name: string){
    this.incomeService.addIncomeCatRetId(name, this.currentUserId)
  }
}
