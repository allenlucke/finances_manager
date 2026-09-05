import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiClient } from './api';
import { Account, NetWorthRow } from './models';

describe('ApiClient', () => {
  let api: ApiClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(ApiClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests accounts from the versioned path', () => {
    const expected: Account[] = [
      {
        id: 1,
        name: 'Chase Sapphire',
        accountType: 'credit_card',
        ledgerEntityId: 1,
        currency: 'USD',
        active: true,
        balance: -696.31,
        transactionCount: 2,
        lastActivity: '2026-08-16',
        mask: '1234',
        balanceSource: 'transactions',
        balanceAsOf: null,
        costBasis: null,
      },
    ];

    let actual: Account[] | undefined;
    api.accounts().subscribe((accounts) => (actual = accounts));

    const request = httpMock.expectOne('/api/v1/accounts');
    expect(request.request.method).toBe('GET');
    request.flush(expected);

    expect(actual).toEqual(expected);
    // A credit card that is owed money reports a negative balance; see the sign convention.
    expect(actual![0].balance).toBeLessThan(0);
  });

  it('separates per-entity net worth from the combined row', () => {
    const rows: NetWorthRow[] = [
      { ledgerEntityId: 1, netWorth: 1607.38 },
      { ledgerEntityId: 2, netWorth: 5000 },
      { ledgerEntityId: null, netWorth: 6607.38 },
    ];

    let actual: NetWorthRow[] | undefined;
    api.netWorth().subscribe((result) => (actual = result));
    httpMock.expectOne('/api/v1/reports/net-worth').flush(rows);

    // The combined figure is the row with no entity — v_net_worth's GROUPING SETS row.
    const combined = actual!.find((row) => row.ledgerEntityId === null);
    expect(combined?.netWorth).toBe(6607.38);
  });

  it('sends the date range as query parameters when listing transactions', () => {
    api.transactions('2026-08-01', '2026-08-31').subscribe();

    const request = httpMock.expectOne((candidate) => candidate.url === '/api/v1/transactions');
    expect(request.request.params.get('from')).toBe('2026-08-01');
    expect(request.request.params.get('to')).toBe('2026-08-31');
    request.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 });
  });

  it('posts the amount as a string so it is never rounded by a JS number', () => {
    api
      .createTransaction({
        accountId: 1,
        transactionDate: '2026-08-14',
        amount: '84.31',
        direction: 'debit',
        description: 'KROGER #4521',
      })
      .subscribe();

    const request = httpMock.expectOne('/api/v1/transactions');
    expect(typeof request.request.body.amount).toBe('string');
    expect(request.request.body.amount).toBe('84.31');
    request.flush([]);
  });
});
