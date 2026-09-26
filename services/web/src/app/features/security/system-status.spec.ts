import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { SystemStatusComponent } from './system-status';

/** Every row is a verdict in words; colour never carries it alone; a failed check is a sentence. */
describe('SystemStatusComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<SystemStatusComponent>>;

  const text = () =>
    ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');

  const quiet = {
    now: '2026-09-26T18:00:00Z',
    zone: 'America/Chicago',
    aiService: 'unreachable',
    marketData: { provider: 'none', available: false, detail: "MARKET_DATA_PROVIDER is 'none'." },
    marketScheduled: false,
    marketRefreshEvery: 'PT5M',
    lastMarketRefresh: null,
    lastMarketRefreshOutcome: null,
    broker: {
      broker: 'none',
      available: false,
      paper: true,
      marketOpen: null,
      buyingPower: null,
      portfolioValue: null,
      detail: "TRADING_BROKER is 'none'.",
    },
    tradingEnabled: false,
    tradingDailyCap: 1000,
    digestScheduled: true,
    lastDigest: null,
    lastSnapshot: null,
    activeStrategies: 0,
    strategyEvaluateEvery: 'PT1M',
    notifierConfigured: false,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    fixture = TestBed.createComponent(SystemStatusComponent);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('says what is off, unreachable or not yet run, in words', () => {
    backend.expectOne('/api/v1/system/status').flush(quiet);
    fixture.detectChanges();

    expect(text()).toContain('unreachable — imports and categorization will fail');
    expect(text()).toContain("off (MARKET_DATA_PROVIDER is 'none'.)");
    expect(text()).toContain('never since the API started');
    expect(text()).toContain("none (TRADING_BROKER is 'none'.); trading off");
    expect(text()).toContain('scheduled, has not run yet');
    expect(text()).toContain('no channel (NTFY_URL is blank)');
    expect(text()).toContain('none yet; the first is taken by the housekeeping tick');
    expect(text()).toContain('none live');
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.sr-only').length,
    ).toBeGreaterThan(0);
  });

  it('says what ran and whether it got through', () => {
    backend.expectOne('/api/v1/system/status').flush({
      ...quiet,
      aiService: 'ok',
      marketData: { provider: 'alpaca', available: true, detail: null },
      marketScheduled: true,
      lastMarketRefresh: '2026-09-26T17:55:00Z',
      lastMarketRefreshOutcome: '4 quotes from alpaca',
      broker: { ...quiet.broker, broker: 'alpaca_paper', available: true, detail: null },
      tradingEnabled: true,
      lastDigest: {
        id: 3,
        forDate: '2026-09-26',
        ranAt: '2026-09-26T12:30:00Z',
        manual: false,
        items: 2,
        sent: false,
        deliveryError: 'ntfy answered 502',
        body: '',
      },
      lastSnapshot: '2026-09-26',
      activeStrategies: 2,
      notifierConfigured: true,
    });
    fixture.detectChanges();

    expect(text()).toContain('answering');
    expect(text()).toContain('alpaca, polling every PT5M');
    expect(text()).toContain('4 quotes from alpaca');
    expect(text()).toContain('alpaca_paper (paper), trading on, cap $1,000.00 a day');
    expect(text()).toContain('last run 2026-09-26, 2 item(s), not delivered: ntfy answered 502');
    expect(text()).toContain('last taken 2026-09-26');
    expect(text()).toContain('2 live, asked every PT1M');
  });

  it('a failed check is a sentence', () => {
    backend.expectOne('/api/v1/system/status').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(text()).toContain('Could not ask the API how it is doing');
  });
});
