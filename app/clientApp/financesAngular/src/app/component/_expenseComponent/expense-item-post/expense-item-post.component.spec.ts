import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ExpenseItemPostComponent } from './expense-item-post.component';

describe('ExpenseItemPostComponent', () => {
  let component: ExpenseItemPostComponent;
  let fixture: ComponentFixture<ExpenseItemPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ExpenseItemPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ExpenseItemPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
