import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { DashboardComponent } from './dashboard';

/**
 * A failed request must never look like an empty ledger, and a missing net worth must never look
 * like $0.00. Both were true before.
 */
describe('DashboardComponent', () => {
  let backend: HttpTestingController;
  let component: Record<string, any>;
  let fixture: ReturnType<typeof TestBed.createComponent<DashboardComponent>>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  function answer(path: string, body: unknown, status = 200) {
    const pending = backend.match((request) => request.url.startsWith(path));
    expect(pending.length, path).toBeGreaterThan(0);
    for (const request of pending) {
      request.flush(body as never, { status, statusText: status === 200 ? 'OK' : 'Error' });
    }
  }

  it('shows a dash, not $0.00, when net worth cannot be loaded', () => {
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', null, 500);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);

    expect(component['combined']()).toBeNull();
    expect(component['anyError']()).toContain('Could not load net worth.');
  });

  it('a failed accounts request is an error, not "no accounts yet"', () => {
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', []);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', null, 500);

    expect(component['accounts'].error()).toBe('Could not load accounts.');
    expect(component['accounts'].isEmpty()).toBe(false);
  });

  it('a genuinely empty ledger is empty', () => {
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', [
      { ledgerEntityId: null, netWorth: 0, snapshotAccounts: 0, oldestSnapshot: null },
    ]);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);

    expect(component['accounts'].isEmpty()).toBe(true);
    expect(component['combined']()).toBe(0);
  });

  it('says when part of net worth is a holdings snapshot, and how old it is', () => {
    // A brokerage balance is its last positions import. A deposit made since is not in the
    // figure until the next one, so the number without its date can be quietly wrong.
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', [
      { ledgerEntityId: null, netWorth: 12000, snapshotAccounts: 1, oldestSnapshot: '2026-08-27' },
    ]);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);

    expect(component['snapshotNote']()).toContain('1 brokerage account valued as of');
    expect(component['snapshotNote']()).toContain('2026');
  });

  it('says nothing about snapshots when every balance comes from the ledger', () => {
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', [
      { ledgerEntityId: null, netWorth: 12000, snapshotAccounts: 0, oldestSnapshot: null },
    ]);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);

    expect(component['snapshotNote']()).toBeNull();
  });

  it('says, in text, when a mismatch was computed by summing the whole ledger — and only then', () => {
    // The API returns `baseline` per row; the branch on it was dead for two weeks because the
    // endpoint did not select the column, and no spec rendered this template to notice. Two rows,
    // one of each kind, so the assertion proves the sentence appears exactly where it should.
    const row = {
      accountId: 7,
      periodStart: '2026-08-01',
      periodEnd: '2026-08-31',
      closingBalance: 100,
      computedBalance: 90,
      difference: 10,
      reconciled: false,
    };
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', []);
    answer('/api/v1/reports/reconciliation', [
      { ...row, statementId: 1, baseline: 'full_history' },
      { ...row, statementId: 2, periodEnd: '2026-07-31', baseline: 'opening_balance' },
    ]);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('2 statement(s) do not match the ledger');
    expect(text.match(/summed from the beginning of the ledger/g)).toHaveLength(1);
    expect(text).toContain('earlier history was never imported');
  });

  it('refresh moves the clock so the month follows the calendar', () => {
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', []);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);
    const before = component['now']();

    component['refresh']();

    expect(component['now']().getTime()).toBeGreaterThanOrEqual(before.getTime());
    answer('/api/v1/entities', []);
    answer('/api/v1/reports/net-worth', []);
    answer('/api/v1/reports/reconciliation', []);
    answer('/api/v1/reports/spend-vs-target', []);
    answer('/api/v1/accounts', []);
  });
});
