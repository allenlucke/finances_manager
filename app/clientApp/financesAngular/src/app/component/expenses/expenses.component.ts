import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { ExpensesService } from 'src/app/service/expenses.service';
import { ExpenseCategory } from 'src/app/_models/expense-category';

@Component({
  selector: 'app-expenses',
  templateUrl: './expenses.component.html',
  styleUrls: ['./expenses.component.css']
})
export class ExpensesComponent implements OnInit {
  newCategoryForm!: FormGroup;
  loading = false;
  submitted = false;
  allExpCategories! : ExpenseCategory[]; 
  error = '';
  currentUserId! : number;
  returnedId! : number;

  constructor(
    private formBuilder: FormBuilder,
    private expensesService : ExpensesService
    ) { }

  ngOnInit(){
    this.loading = true;

    this.getAllExpenseCategories();

    this.currentUserId = Number(localStorage.getItem('currentUserId'));

    console.log(localStorage.getItem('currentUserId'));

    this.newCategoryForm = this.formBuilder.group({
      name: ['', Validators.required]
  });
  }

  // convenience getter for easy access to form fields
  get f() { return this.newCategoryForm.controls; }

  getAllExpenseCategories(){
    this.expensesService.getAllExpCat().pipe(first()).subscribe(allExpCategories => {
      this.allExpCategories = allExpCategories;
      this.loading = false;
  })
  }

  onSubmit() {
    this.submitted = true;

    // stop here if form is invalid
    if (this.newCategoryForm!.invalid) {
        return;
    }

    // this.loading = true;
    this.postExpenseCategory(this.f.name.value)

    this.getAllExpenseCategories();
    this.newCategoryForm.reset();
    this.f.name.untouched;

    this.submitted = false;
  }

  postExpenseCategory(name: string){
    this.expensesService.addExpCatRetId(name, this.currentUserId).pipe(first()).subscribe(returnedId => {
          this.returnedId = returnedId;
      })
      this.getAllExpenseCategories();
  }
}
