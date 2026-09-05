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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    const fixture = TestBed.createComponent(DashboardComponent);
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
