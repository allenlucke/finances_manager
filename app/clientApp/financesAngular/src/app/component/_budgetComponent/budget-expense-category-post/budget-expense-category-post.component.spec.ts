import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BudgetExpenseCategoryPostComponent } from './budget-expense-category-post.component';

describe('BudgetExpenseCategoryPostComponent', () => {
  let component: BudgetExpenseCategoryPostComponent;
  let fixture: ComponentFixture<BudgetExpenseCategoryPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ BudgetExpenseCategoryPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(BudgetExpenseCategoryPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
