import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { IncomeService } from 'src/app/service/income.service';
import { IncomeCategory } from 'src/app/_models/income-category';

@Component({
  selector: 'app-income',
  templateUrl: './income.component.html',
  styleUrls: ['./income.component.css']
})
export class IncomeComponent implements OnInit {
  newCategoryForm!: FormGroup;
  loading = false;
  submitted = false;
  error = '';
  allIncomeCategories! : IncomeCategory[];
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder: FormBuilder,
    private incomeService: IncomeService,
  ) { }

  ngOnInit() {

    this.loading = true;

    this.getAllIncomeCategories();

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
    this.getAllIncomeCategories();
    this.newCategoryForm.reset();
    this.f.name.untouched;

    this.submitted = false;
  }

  getAllIncomeCategories(){
    this.incomeService.getAllIncomeCat().pipe(first()).subscribe(allIncomeCategories => {
      this.allIncomeCategories = allIncomeCategories;
      this.loading = false;
    })
  }

  postIncomeCategory(name: string){
    this.incomeService.addIncomeCatRetId(name, this.currentUserId).pipe(first()).subscribe(returnedId => {
      this.returnedId = returnedId;
      })
      this.getAllIncomeCategories();
  }

}
