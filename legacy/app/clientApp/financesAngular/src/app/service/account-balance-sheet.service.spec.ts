import { TestBed } from '@angular/core/testing';

import { AccountBalanceSheetService } from './account-balance-sheet.service';

describe('AccountBalanceSheetService', () => {
  let service: AccountBalanceSheetService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(AccountBalanceSheetService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
