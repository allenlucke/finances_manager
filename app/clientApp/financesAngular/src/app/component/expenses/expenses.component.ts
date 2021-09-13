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


    console.log("In on submit()")
    // stop here if form is invalid
    if (this.newCategoryForm!.invalid) {
        return;
    }

    this.loading = true;
    this.postExpenseCategory(this.f.name.value)
  }

  postExpenseCategory(name: string){
    this.expensesService.addExpCatRetId(name, this.currentUserId)
      // .pipe(first()).subscribe()
      this.getAllExpenseCategories;
  }

}
