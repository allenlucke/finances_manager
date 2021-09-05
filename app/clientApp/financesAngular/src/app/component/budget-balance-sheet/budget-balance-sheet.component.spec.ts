import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BudgetBalanceSheetComponent } from './budget-balance-sheet.component';

describe('BudgetBalanceSheetComponent', () => {
  let component: BudgetBalanceSheetComponent;
  let fixture: ComponentFixture<BudgetBalanceSheetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ BudgetBalanceSheetComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(BudgetBalanceSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
