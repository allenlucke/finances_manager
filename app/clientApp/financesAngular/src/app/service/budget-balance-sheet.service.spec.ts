import { TestBed } from '@angular/core/testing';

import { BudgetBalanceSheetService } from './budget-balance-sheet.service';

describe('BudgetBalanceSheetService', () => {
  let service: BudgetBalanceSheetService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(BudgetBalanceSheetService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
