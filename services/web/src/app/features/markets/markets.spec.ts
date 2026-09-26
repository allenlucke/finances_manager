import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { MarketsComponent } from './markets';

/**
 * A price is not money, and a fake price is not a price. Every claim this screen makes about
 * where a number came from is asserted in the DOM.
 */
describe('MarketsComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<MarketsComponent>>;
  let component: Record<string, any>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const liveStatus = {
    provider: 'alpaca',
    available: true,
    detail: null,
    scheduled: true,
    refreshEvery: 'PT5M',
    notifierConfigured: false,
    lastRefreshAt: '2026-09-25T20:00:00Z',
    lastRefreshOutcome: '2 quotes from alpaca',
  };

  const aapl = {
    securityId: 1,
    symbol: 'AAPL',
    name: 'Apple',
    price: 189.3,
    previousClose: 187.1,
    changePct: 1.1758,
    asOf: '2026-09-25T19:59:58Z',
    source: 'alpaca',
    watched: true,
    held: false,
  };

  type Answer = { body: unknown; status?: number };
  function answerAll(overrides: Partial<Record<string, Answer>> = {}) {
    const answers: Record<string, Answer> = {
      '/api/v1/market/status': { body: liveStatus },
      '/api/v1/market/watchlist': { body: [] },
      '/api/v1/market/quotes': { body: [] },
      '/api/v1/market/holdings': { body: [] },
      '/api/v1/market/alerts/events': { body: [] },
      '/api/v1/market/alerts': { body: [] },
      ...overrides,
    };
    for (const [path, answer] of Object.entries(answers)) {
      const pending = backend.match((r) => r.url === path || r.url.startsWith(path + '?'));
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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(MarketsComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it("shows the quote, today's move with its sign, and where it came from", () => {
    answerAll({ '/api/v1/market/quotes': { body: [aapl] } });
    fixture.detectChanges();

    expect(text()).toContain('AAPL');
    expect(text()).toContain('$189.30');
    expect(text()).toContain('+1.18%');
    expect(text()).toContain('Quotes from alpaca');
    expect(text()).not.toContain('fake prices');
    expect(text()).toContain('alerts are recorded but not pushed anywhere');
  });

  it('says market data is off, in text, and disables the refresh', () => {
    answerAll({
      '/api/v1/market/status': {
        body: {
          ...liveStatus,
          provider: 'none',
          available: false,
          detail: "MARKET_DATA_PROVIDER is 'none'.",
        },
      },
    });
    fixture.detectChanges();

    expect(text()).toContain('Market data is off');
    expect(text()).toContain('MARKET_DATA_PROVIDER');
    const refresh = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(
      (b) => b.textContent?.includes('Refresh quotes'),
    );
    expect(refresh?.disabled).toBe(true);
  });

  it('labels fake prices as fake, on the banner and on every row', () => {
    answerAll({
      '/api/v1/market/status': { body: { ...liveStatus, provider: 'fake' } },
      '/api/v1/market/quotes': { body: [{ ...aapl, source: 'fake' }] },
    });
    fixture.detectChanges();

    expect(text()).toContain('These are fake prices');
    expect(text().match(/fake/g)?.length ?? 0).toBeGreaterThanOrEqual(2);
  });

  it('shows a holding two ways and says which one the balance uses', () => {
    answerAll({
      '/api/v1/market/holdings': {
        body: [
          {
            accountId: 1,
            accountName: 'Brokerage',
            securityId: 2,
            symbol: 'FXAIX',
            securityName: 'Fidelity 500',
            cash: false,
            snapshotAsOf: '2026-08-27',
            quantity: 10,
            snapshotPrice: 200,
            snapshotValue: 2000,
            livePrice: 201.44,
            quoteAsOf: '2026-09-25T20:00:00Z',
            quoteSource: 'alpaca',
            liveValue: 2014.4,
          },
        ],
      },
    });
    fixture.detectChanges();

    expect(text()).toContain('$2,000.00');
    expect(text()).toContain('as of 2026-08-27');
    expect(text()).toContain('$2,014.40');
    expect(text()).toContain('Balances and net worth use the first');
  });

  it('sends the alert threshold as a string and the symbol uppercased', () => {
    answerAll();
    component['alertForm'].setValue({
      symbol: 'aapl',
      rule: 'above',
      threshold: '190.50',
      note: '',
    });
    component['addAlert']();

    const request = backend.expectOne('/api/v1/market/alerts');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      symbol: 'AAPL',
      rule: 'above',
      threshold: '190.50',
      note: null,
    });
    request.flush({ id: 1 });
    answerAll();
  });

  it('describes an alert in words and shows whether it is armed', () => {
    answerAll({
      '/api/v1/market/alerts': {
        body: [
          {
            id: 1,
            securityId: 1,
            symbol: 'AAPL',
            rule: 'above',
            threshold: 190,
            active: true,
            armed: false,
            lastFiredAt: '2026-09-25T15:05:00Z',
            note: 'take profit',
          },
          {
            id: 2,
            securityId: 1,
            symbol: 'MSFT',
            rule: 'pct_move',
            threshold: 3,
            active: false,
            armed: true,
            lastFiredAt: null,
            note: null,
          },
        ],
      },
    });
    fixture.detectChanges();

    expect(text()).toContain('rises above $190.00');
    expect(text()).toContain('fired — re-arms when the condition clears');
    expect(text()).toContain('moves more than 3%');
    expect(text()).toContain('paused');
  });

  it('shows an undelivered firing with the reason', () => {
    answerAll({
      '/api/v1/market/alerts/events': {
        body: [
          {
            id: 1,
            alertId: 1,
            symbol: 'AAPL',
            rule: 'above',
            threshold: 190,
            price: 195,
            firedAt: '2026-09-25T15:20:00Z',
            message: 'AAPL is above 190: 195',
            delivered: false,
            deliveryError: 'No notification channel is configured (NTFY_URL is blank).',
          },
        ],
      },
    });
    fixture.detectChanges();

    expect(text()).toContain('AAPL is above 190: 195');
    expect(text()).toContain('NTFY_URL');
  });

  it('a failed quotes request is a sentence, not "nothing watched yet"', () => {
    answerAll({ '/api/v1/market/quotes': { body: null, status: 500 } });
    fixture.detectChanges();

    expect(text()).toContain('Could not load quotes');
    expect(text()).not.toContain('Nothing watched yet');
  });
});
