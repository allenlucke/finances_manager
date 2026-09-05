import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { AccountsComponent } from './accounts';

describe('AccountsComponent', () => {
  let backend: HttpTestingController;
  let component: Record<string, any>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    component = TestBed.createComponent(AccountsComponent).componentInstance as unknown as Record<
      string,
      any
    >;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('takes net worth from the API rather than summing balances with floats', () => {
    // Three balances that do not sum cleanly in binary. The API's NUMERIC figure is authoritative;
    // reduce(+) in the browser produced a second net worth that could disagree with the dashboard.
    backend.expectOne('/api/v1/entities').flush([]);
    backend.expectOne('/api/v1/accounts').flush([
      {
        id: 1,
        name: 'A',
        accountType: 'checking',
        ledgerEntityId: 1,
        currency: 'USD',
        active: true,
        balance: 0.1,
        transactionCount: 1,
        lastActivity: null,
        mask: null,
        balanceSource: 'transactions',
        balanceAsOf: null,
        costBasis: null,
      },
      {
        id: 2,
        name: 'B',
        accountType: 'checking',
        ledgerEntityId: 1,
        currency: 'USD',
        active: true,
        balance: 0.2,
        transactionCount: 1,
        lastActivity: null,
        mask: null,
        balanceSource: 'transactions',
        balanceAsOf: null,
        costBasis: null,
      },
    ]);
    backend.expectOne('/api/v1/reports/net-worth').flush([{ ledgerEntityId: null, netWorth: 0.3 }]);
    backend.expectOne('/api/v1/holdings').flush([]);

    expect(component['combined']()).toBe(0.3);
    expect(component['total']).toBeUndefined();
  });

  it('groups holdings under the account that holds them', () => {
    backend.expectOne('/api/v1/entities').flush([]);
    backend.expectOne('/api/v1/accounts').flush([
      {
        id: 7,
        name: 'Brokerage',
        accountType: 'brokerage',
        ledgerEntityId: 1,
        currency: 'USD',
        active: true,
        balance: 3100,
        transactionCount: 0,
        lastActivity: null,
        mask: null,
        balanceSource: 'holdings',
        balanceAsOf: '2026-08-27',
        costBasis: null,
      },
    ]);
    backend.expectOne('/api/v1/reports/net-worth').flush([]);
    backend.expectOne('/api/v1/holdings').flush([
      {
        id: 1,
        accountId: 7,
        symbol: 'AAPL',
        name: 'APPLE',
        cash: false,
        securityType: 'unknown',
        asOf: '2026-08-27',
        quantity: 10,
        lastPrice: 220,
        marketValue: 2200,
        costBasis: 1800,
        totalGainLoss: 400,
      },
      {
        id: 2,
        accountId: 7,
        symbol: 'SPAXX',
        name: null,
        cash: true,
        securityType: 'money_market',
        asOf: '2026-08-27',
        quantity: null,
        lastPrice: null,
        marketValue: 900,
        costBasis: null,
        totalGainLoss: null,
      },
    ]);

    const groups = component['holdingsByAccount']();
    expect(groups).toHaveLength(1);
    expect(groups[0].account.name).toBe('Brokerage');
    expect(groups[0].asOf).toBe('2026-08-27');
    expect(groups[0].rows.map((r: { symbol: string }) => r.symbol)).toEqual(['AAPL', 'SPAXX']);
  });
});
