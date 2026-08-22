import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ExpensesService } from 'src/app/service/expenses.service';

@Component({
  selector: 'app-expense-category-post',
  templateUrl: './expense-category-post.component.html',
  styleUrls: ['./expense-category-post.component.css']
})
export class ExpenseCategoryPostComponent implements OnInit {
  newCategoryForm!: FormGroup;
  loading = false;
  submitted = false;
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder: FormBuilder,
    private expensesService : ExpensesService,
  ) { }

  ngOnInit(){
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

      this.postExpenseCategory(this.f.name.value)
      this.newCategoryForm.reset();
      this.f.name.untouched;

      this.submitted = false;
    }

    postExpenseCategory(name: string){
      this.expensesService.addExpCatRetId(name, this.currentUserId);
    }
}
