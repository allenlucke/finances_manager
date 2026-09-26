import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstOfThisMonth } from '../../core/money';
import { DashboardComponent } from './dashboard';

/**
 * A failed request must never look like an empty ledger, and a missing net worth must never look
 * like $0.00. Both were true before. Every message a person reads here is asserted in the DOM.
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

  type Answer = { body: unknown; status?: number };
  const ok = (body: unknown): Answer => ({ body });
  const failed = (): Answer => ({ body: null, status: 500 });

  /** Answers the five requests the dashboard fires, with defaults for the ones a test ignores. */
  function answerAll(overrides: Partial<Record<string, Answer>> = {}) {
    // The needs-a-look card is a child; it asks for its data when the template first renders,
    // and a reload of this screen does not repeat those two requests.
    fixture.detectChanges();
    const answers: Record<string, Answer> = {
      '/api/v1/digest/preview': ok([]),
      '/api/v1/reminders': ok([]),
      '/api/v1/entities': ok([]),
      '/api/v1/reports/net-worth': ok([]),
      '/api/v1/reports/net-worth/history': ok([]),
      '/api/v1/reports/reconciliation': ok([]),
      '/api/v1/reports/spend-vs-target': ok([]),
      '/api/v1/reports/monthly-totals': ok([]),
      '/api/v1/accounts': ok([]),
      ...overrides,
    };
    for (const [path, answer] of Object.entries(answers)) {
      const pending = backend.match(
        (request) => request.url === path || request.url.startsWith(path + '?'),
      );
      if (
        pending.length === 0 &&
        (path.startsWith('/api/v1/digest') || path.startsWith('/api/v1/reminders'))
      )
        continue;
      expect(pending.length, path).toBeGreaterThan(0);
      const status = answer.status ?? 200;
      for (const request of pending) {
        request.flush(answer.body as never, {
          status,
          statusText: status === 200 ? 'OK' : 'Error',
        });
      }
    }
  }

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  it('shows a dash, not $0.00, when net worth cannot be loaded', () => {
    answerAll({ '/api/v1/reports/net-worth': failed() });
    fixture.detectChanges();

    expect(component['combined']()).toBeNull();
    expect(text()).toContain('Could not load net worth.');
    expect(text()).not.toContain('$0.00');
  });

  it('a failed accounts request is an error, not "no accounts yet"', () => {
    answerAll({ '/api/v1/accounts': failed() });
    fixture.detectChanges();

    expect(text()).toContain('Could not load accounts.');
    expect(text()).not.toContain('No accounts yet');
  });

  it('a genuinely empty ledger is empty', () => {
    answerAll({
      '/api/v1/reports/net-worth': ok([
        { ledgerEntityId: null, netWorth: 0, snapshotAccounts: 0, oldestSnapshot: null },
      ]),
    });
    fixture.detectChanges();

    expect(text()).toContain('No accounts yet');
    expect(component['combined']()).toBe(0);
  });

  it('says when part of net worth is a holdings snapshot, and how old it is', () => {
    // A brokerage balance is its last positions import. A deposit made since is not in the
    // figure until the next one, so the number without its date can be quietly wrong.
    answerAll({
      '/api/v1/reports/net-worth': ok([
        {
          ledgerEntityId: null,
          netWorth: 12000,
          snapshotAccounts: 1,
          oldestSnapshot: '2026-08-27',
        },
      ]),
    });
    fixture.detectChanges();

    expect(text()).toContain('1 brokerage account valued as of');
    expect(text()).toContain('2026');
  });

  it('says nothing about snapshots when every balance comes from the ledger', () => {
    answerAll({
      '/api/v1/reports/net-worth': ok([
        { ledgerEntityId: null, netWorth: 12000, snapshotAccounts: 0, oldestSnapshot: null },
      ]),
    });
    fixture.detectChanges();

    expect(text()).not.toContain('valued as of');
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
    answerAll({
      '/api/v1/reports/reconciliation': ok([
        { ...row, statementId: 1, baseline: 'full_history' },
        { ...row, statementId: 2, periodEnd: '2026-07-31', baseline: 'opening_balance' },
      ]),
    });
    fixture.detectChanges();

    expect(text()).toContain('2 statement(s) do not match the ledger');
    expect(text().match(/summed from the beginning of the ledger/g)).toHaveLength(1);
    expect(text()).toContain('earlier history was never imported');
  });

  it('offers to remove a checkpoint, and reloads the report when it is gone', () => {
    answerAll({
      '/api/v1/reports/reconciliation': ok([
        {
          statementId: 5,
          accountId: 7,
          periodStart: '2026-08-01',
          periodEnd: '2026-08-31',
          closingBalance: 100,
          computedBalance: 90,
          difference: 10,
          reconciled: false,
          baseline: 'full_history',
        },
      ]),
    });
    fixture.detectChanges();
    const button = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(
      (b) => b.textContent?.includes('Remove checkpoint'),
    );
    expect(button, 'a remove button on the mismatch row').toBeDefined();

    button!.click();

    backend
      .expectOne('/api/v1/statements/5')
      .flush(null, { status: 204, statusText: 'No Content' });
    backend.expectOne('/api/v1/reports/reconciliation').flush([]);
    fixture.detectChanges();
    expect(text()).not.toContain('do not match the ledger');
  });

  it("shows the month's money out, and how much of it is still uncategorized", () => {
    // Every row in the spending card is categorized spend, so a month that is mostly still in
    // the review queue looked cheap here with nothing to say so.
    answerAll({
      '/api/v1/reports/monthly-totals': ok([
        {
          month: firstOfThisMonth(),
          ledgerEntityId: null,
          moneyOut: 1234.5,
          moneyIn: 3000,
          uncategorizedOut: 400,
          uncategorizedCount: 7,
          transactionCount: 20,
        },
        {
          month: firstOfThisMonth(),
          ledgerEntityId: 1,
          moneyOut: 1234.5,
          moneyIn: 3000,
          uncategorizedOut: 400,
          uncategorizedCount: 7,
          transactionCount: 20,
        },
      ]),
    });
    fixture.detectChanges();

    expect(text()).toContain('$1,234.50');
    expect(text()).toContain('out this month');
    expect(text()).toContain('$400.00 in 7 row(s) not yet categorized');
  });

  it('says nothing about uncategorized rows when there are none', () => {
    answerAll({
      '/api/v1/reports/monthly-totals': ok([
        {
          month: firstOfThisMonth(),
          ledgerEntityId: null,
          moneyOut: 50,
          moneyIn: 0,
          uncategorizedOut: 0,
          uncategorizedCount: 0,
          transactionCount: 1,
        },
      ]),
    });
    fixture.detectChanges();

    expect(text()).toContain('$50.00');
    expect(text()).not.toContain('not yet categorized');
  });

  it('refresh moves the clock so the month follows the calendar', () => {
    answerAll();
    const before = component['now']();

    component['refresh']();

    expect(component['now']().getTime()).toBeGreaterThanOrEqual(before.getTime());
    answerAll();
  });

  it('draws the net worth trend and says how far it moved since the first snapshot', () => {
    answerAll({
      '/api/v1/reports/net-worth/history': ok([
        { asOf: '2026-06-28', ledgerEntityId: null, netWorth: 1000, snapshotAccounts: 0 },
        { asOf: '2026-06-28', ledgerEntityId: 1, netWorth: 1000, snapshotAccounts: 0 },
        { asOf: '2026-09-26', ledgerEntityId: null, netWorth: 1500, snapshotAccounts: 0 },
        { asOf: '2026-09-26', ledgerEntityId: 1, netWorth: 1500, snapshotAccounts: 0 },
      ]),
    });
    fixture.detectChanges();

    expect(text()).toContain('+$500.00 since 2026-06-28');
    expect((fixture.nativeElement as HTMLElement).querySelector('.trend polyline')).not.toBeNull();
  });

  it('with no history yet, says a snapshot is taken daily rather than drawing nothing', () => {
    answerAll();
    fixture.detectChanges();

    expect(text()).toContain('No history yet');
  });
});
