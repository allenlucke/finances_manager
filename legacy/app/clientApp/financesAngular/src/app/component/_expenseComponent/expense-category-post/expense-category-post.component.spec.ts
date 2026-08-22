import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExpenseCategoryPostComponent } from './expense-category-post.component';

describe('ExpenseCategoryPostComponent', () => {
  let component: ExpenseCategoryPostComponent;
  let fixture: ComponentFixture<ExpenseCategoryPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ExpenseCategoryPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ExpenseCategoryPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
