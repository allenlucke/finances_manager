import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountBalanceSheetComponent } from './account-balance-sheet.component';

describe('AccountBalanceSheetComponent', () => {
  let component: AccountBalanceSheetComponent;
  let fixture: ComponentFixture<AccountBalanceSheetComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ AccountBalanceSheetComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountBalanceSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
