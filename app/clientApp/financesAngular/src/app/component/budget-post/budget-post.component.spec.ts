import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BudgetPostComponent } from './budget-post.component';

describe('BudgetPostComponent', () => {
  let component: BudgetPostComponent;
  let fixture: ComponentFixture<BudgetPostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ BudgetPostComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(BudgetPostComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
