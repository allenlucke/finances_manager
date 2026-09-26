import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { CashflowComponent } from './cashflow';

/** Recurring charges show their evidence, a missing one says so in words, and a failure is a sentence. */
describe('CashflowComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<CashflowComponent>>;

  const text = () =>
    ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');

  const report = {
    days: 30,
    expectedOut: 135.49,
    expectedIn: 0,
    monthlyRecurringOut: 135.49,
    liquidCash: 84.56,
    runwayWeeks: 2.7,
    muted: [{ key: 'z', label: 'CORNER COFFEE' }],
    series: [
      {
        key: 'a',
        label: 'NETFLIX.COM',
        accountName: 'Everyday checking',
        direction: 'debit',
        cadence: 'monthly',
        typicalAmount: 15.49,
        amountVaries: false,
        lastAmount: 15.49,
        lastDate: '2026-09-15',
        nextExpected: '2026-10-15',
        occurrences: 6,
        status: 'upcoming',
      },
      {
        key: 'b',
        label: 'PLANET FITNESS',
        accountName: 'Everyday checking',
        direction: 'debit',
        cadence: 'monthly',
        typicalAmount: 40,
        amountVaries: false,
        lastAmount: 40,
        lastDate: '2026-06-03',
        nextExpected: '2026-07-03',
        occurrences: 5,
        status: 'missing',
      },
      {
        key: 'c',
        label: 'CITY POWER',
        accountName: 'Everyday checking',
        direction: 'debit',
        cadence: 'monthly',
        typicalAmount: 120,
        amountVaries: true,
        lastAmount: 310,
        lastDate: '2026-09-20',
        nextExpected: '2026-10-20',
        occurrences: 6,
        status: 'on track',
      },
    ],
    upcoming: [
      {
        date: '2026-10-15',
        label: 'NETFLIX.COM',
        accountName: 'Everyday checking',
        direction: 'debit',
        amount: 15.49,
      },
      {
        date: '2026-10-20',
        label: 'CITY POWER',
        accountName: 'Everyday checking',
        direction: 'debit',
        amount: 120,
      },
    ],
    anomalies: [
      {
        kind: 'unusual_amount',
        severity: 'medium',
        text: 'CITY POWER was $310.00 on 2026-09-20, usually about $120.00',
        date: '2026-09-20',
      },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(CashflowComponent);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  const flush = (body: unknown, status = 200) =>
    backend
      .expectOne((r) => r.url === '/api/v1/cashflow')
      .flush(body as never, { status, statusText: status === 200 ? 'OK' : 'Error' });

  it('shows each series with its evidence, the missing one in words, and the anomaly', () => {
    flush(report);
    fixture.detectChanges();

    expect(text()).toContain('NETFLIX.COM');
    expect(text()).toContain('seen 6 times');
    expect(text()).toContain('-$15.49');
    expect(text()).toContain('expected 2026-10-15');
    expect(text()).toContain('missing — expected around 2026-07-03, not seen since 2026-06-03');
    expect(text()).toContain('varies');
    expect(text()).toContain('CITY POWER was $310.00 on 2026-09-20, usually about $120.00');
    expect(text()).toContain('-$135.49');
    expect(text()).toContain('1 missing');
    expect(text()).toContain('2.7 weeks');
    expect(text()).toContain(
      '$84.56 on hand covers about 2.7 weeks of recurring charges at $135.49 a month.',
    );
    expect(text()).toContain('Not recurring, you said: CORNER COFFEE');
  });

  it('"not recurring" is remembered by key, and undo sends it back', () => {
    flush(report);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    (
      element.querySelector(
        'button[aria-label="Not recurring: PLANET FITNESS"]',
      ) as HTMLButtonElement
    ).click();
    const mute = backend.expectOne('/api/v1/cashflow/mute');
    expect(mute.request.method).toBe('PUT');
    expect(mute.request.body).toEqual({ key: 'b', label: 'PLANET FITNESS', muted: true });
    mute.flush(report);
    flush(report);
    fixture.detectChanges();

    (
      element.querySelector(
        'button[aria-label="It is recurring after all: CORNER COFFEE"]',
      ) as HTMLButtonElement
    ).click();
    const unmute = backend.expectOne('/api/v1/cashflow/mute');
    expect(unmute.request.body).toEqual({ key: 'z', label: 'CORNER COFFEE', muted: false });
    unmute.flush(report);
    flush(report);
  });

  it('says when nothing recurring has been found, and why', () => {
    flush({
      ...report,
      series: [],
      upcoming: [],
      anomalies: [],
      expectedOut: 0,
      monthlyRecurringOut: 0,
      runwayWeeks: null,
      muted: [],
    });
    fixture.detectChanges();

    expect(text()).toContain('No recurring charges found yet');
    expect(text()).toContain('three occurrences at a steady interval');
    expect(text()).toContain('Nothing recurring is expected in this window');
  });

  it('a failed request is a sentence, not an empty ledger', () => {
    flush(null, 500);
    fixture.detectChanges();

    expect(text()).toContain('Could not read the ledger for recurring charges');
    expect(text()).not.toContain('No recurring charges found yet');
  });
});
