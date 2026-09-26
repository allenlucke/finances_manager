import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { StrategiesComponent } from './strategies';

/**
 * A backtest is a claim: the screen must show its doubts before its numbers and buy-and-hold
 * beside the strategy, and a strategy must read as something that proposes, never trades.
 */
describe('StrategiesComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<StrategiesComponent>>;
  let component: Record<string, any>;

  const element = () => fixture.nativeElement as HTMLElement;
  const text = () => (element().textContent ?? '').replace(/\s+/g, ' ');
  const labelOf = (b: HTMLButtonElement) =>
    [...b.childNodes]
      .filter((n) => (n as Element).tagName !== 'MAT-ICON')
      .map((n) => n.textContent ?? '')
      .join('')
      .replace(/\s+/g, ' ')
      .trim();
  const button = (label: string) =>
    [...element().querySelectorAll('button')].find((b) => labelOf(b).startsWith(label));

  const fakeStatus = {
    provider: 'fake',
    available: true,
    detail: null,
    scheduled: false,
    refreshEvery: 'PT5M',
    notifierConfigured: false,
    lastRefreshAt: null,
    lastRefreshOutcome: null,
  };
  const catalog = [
    {
      kind: 'sma_cross',
      label: 'Moving-average crossover',
      description: 'Long while the fast average is above the slow one.',
      intraday: false,
      params: [
        {
          name: 'fast',
          label: 'Fast average',
          type: 'int',
          description: 'Bars',
          default: '10',
          min: '2',
          max: '500',
        },
        {
          name: 'slow',
          label: 'Slow average',
          type: 'int',
          description: 'Bars',
          default: '30',
          min: '3',
          max: '1000',
        },
      ],
    },
    {
      kind: 'opening_range_breakout',
      label: 'Opening-range breakout',
      description: 'Intraday.',
      intraday: true,
      params: [
        {
          name: 'range_minutes',
          label: 'Opening range',
          type: 'int',
          description: 'Minutes',
          default: '30',
          min: '5',
          max: '120',
        },
      ],
    },
  ];
  const run = {
    id: 3,
    strategyId: null,
    kind: 'sma_cross',
    params: { fast: '10', slow: '30' },
    symbol: 'AAPL',
    timeframe: '1Day',
    start: '2025-09-26',
    end: '2026-09-26',
    initialCash: 10000,
    slippageBps: 5,
    commission: 0,
    outOfSampleFraction: 0.3,
    provider: 'fake',
    bars: 250,
    trades: 7,
    dayTrades: 0,
    totalReturnPct: 3.1,
    benchmarkReturnPct: 8.25,
    maxDrawdownPct: 6.4,
    sharpe: 0.61,
    finalEquity: 10310,
    inSampleReturnPct: 5,
    outOfSampleReturnPct: -1.8,
    warnings: [
      'These bars are fake: a deterministic random walk from the fake provider. The result says nothing about any market.',
      'Buy and hold returned 8.25% over the same bars; the strategy returned 3.10%.',
    ],
    createdAt: '2026-09-26T15:00:00Z',
    result: {
      provider: 'fake',
      strategy: 'sma_cross',
      params: { fast: '10', slow: '30' },
      symbol: 'AAPL',
      timeframe: '1Day',
      start: '2025-09-26',
      end: '2026-09-26',
      metrics: {
        bars: 250,
        trades: 7,
        total_return_pct: '3.10',
        benchmark_return_pct: '8.25',
        max_drawdown_pct: '6.40',
        win_rate_pct: '42.86',
        profit_factor: '1.21',
        avg_trade_pct: '0.44',
        exposure_pct: '51.06',
        sharpe: 0.61,
        final_equity: '10310.00',
        day_trades: 0,
      },
      in_sample: {
        bars: 175,
        trades: 5,
        total_return_pct: '5.00',
        benchmark_return_pct: '6.00',
        max_drawdown_pct: '4.00',
        win_rate_pct: null,
        profit_factor: null,
        avg_trade_pct: null,
        exposure_pct: '50.00',
        sharpe: null,
        final_equity: '10500.00',
        day_trades: 0,
      },
      out_of_sample: {
        bars: 75,
        trades: 2,
        total_return_pct: '-1.80',
        benchmark_return_pct: '2.10',
        max_drawdown_pct: '3.00',
        win_rate_pct: null,
        profit_factor: null,
        avg_trade_pct: null,
        exposure_pct: '53.00',
        sharpe: null,
        final_equity: '9820.00',
        day_trades: 0,
      },
      equity_curve: [
        { ts: '2025-09-26T20:00:00Z', equity: '10000.00' },
        { ts: '2026-09-26T20:00:00Z', equity: '10310.00' },
      ],
      trades: [
        {
          entered_at: '2026-02-02T14:30:00Z',
          exited_at: '2026-02-20T14:30:00Z',
          quantity: 52,
          entry_price: '190.1000',
          exit_price: '193.0000',
          pnl: '150.80',
          return_pct: '1.53',
          reason_in: 'SMA10 crossed above SMA30',
          reason_out: 'SMA10 crossed below SMA30',
          same_day: false,
        },
      ],
      warnings: [],
    },
  };
  const saved = {
    id: 4,
    name: 'Fast cross',
    kind: 'sma_cross',
    params: { fast: '5', slow: '20' },
    symbol: 'AAPL',
    timeframe: '1Day',
    quantity: 3,
    active: false,
    notes: 'on paper first',
    lastEvaluatedAt: null,
    lastEvaluation: null,
    lastSignalAt: '2026-09-25T20:00:00Z',
    lastSignal: 'buy: SMA5 crossed above SMA20',
    createdAt: '2026-09-26T15:00:00Z',
  };

  type Answer = { body: unknown; status?: number };
  function answerAll(overrides: Partial<Record<string, Answer>> = {}) {
    const answers: Record<string, Answer> = {
      '/api/v1/market/status': { body: fakeStatus },
      '/api/v1/strategies/catalog': { body: catalog },
      '/api/v1/strategies': { body: [] },
      '/api/v1/backtests': { body: [] },
      ...overrides,
    };
    for (const [path, answer] of Object.entries(answers)) {
      const pending = backend.match((r) => r.url === path);
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
    fixture = TestBed.createComponent(StrategiesComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('says market data is off, in text, and will not run', () => {
    answerAll({
      '/api/v1/market/status': {
        body: {
          ...fakeStatus,
          provider: 'none',
          available: false,
          detail: "MARKET_DATA_PROVIDER is 'none'.",
        },
      },
    });
    fixture.detectChanges();

    expect(text()).toContain('Market data is off');
    expect(text()).toContain('MARKET_DATA_PROVIDER');
    expect(button('Run backtest')?.disabled).toBe(true);
    expect(button('Evaluate now')?.disabled).toBe(true);
  });

  it("shows the chosen strategy's parameters with their defaults, and keeps intraday rules off daily bars", () => {
    answerAll();
    fixture.detectChanges();

    expect(text()).toContain('These are fake bars');
    expect(text()).toContain('Fast average');
    expect(component['params'].controls['fast'].value).toBe('10');
    expect(component['params'].controls['slow'].value).toBe('30');

    component['form'].controls.kind.setValue('opening_range_breakout');
    fixture.detectChanges();

    expect(component['params'].controls['range_minutes'].value).toBe('30');
    expect(component['params'].controls['fast']).toBeUndefined();
    expect(component['allowedTimeframes']()).not.toContain('1Day');
    expect(component['form'].controls.timeframe.value).toBe('15Min');
  });

  it('runs with strings, then shows the doubts before the numbers and buy-and-hold beside the return', () => {
    answerAll();
    fixture.detectChanges();
    component['form'].patchValue({ symbol: 'aapl', start: '2025-09-26', end: '2026-09-26' });
    component['params'].controls['fast'].setValue('5');
    fixture.detectChanges();

    button('Run backtest')!.click();

    const request = backend.expectOne('/api/v1/backtests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      kind: 'sma_cross',
      params: { fast: '5', slow: '30' },
      symbol: 'AAPL',
      timeframe: '1Day',
      start: '2025-09-26',
      end: '2026-09-26',
      initialCash: '10000',
      slippageBps: '5',
      commission: '0',
      outOfSampleFraction: '0.3',
      strategyId: null,
    });
    request.flush(run);
    backend.expectOne((r) => r.url === '/api/v1/backtests' && r.method === 'GET').flush([run]);
    fixture.detectChanges();

    const body = text();
    expect(body).toContain('Read these before the numbers');
    expect(body).toContain('These bars are fake');
    expect(body.indexOf('Read these before the numbers')).toBeLessThan(body.indexOf('+3.10%'));
    expect(body).toContain('+3.10%');
    expect(body).toContain('+8.25%');
    expect(body).toContain('did not beat buy and hold');
    expect(body).toContain('-6.40%');
    expect(body).toContain('Last 30%, held out');
    expect(body).toContain('-1.80%');
    expect(body).toContain('SMA10 crossed above SMA30');
    expect(element().querySelector('polyline')?.getAttribute('points')).toContain('0.00,');
    expect(component['saveForm'].controls.name.value).toBe('Moving-average crossover on AAPL');
  });

  it('a saved strategy reads as off or proposing, with its last word; switching it on is a PUT', () => {
    answerAll({ '/api/v1/strategies': { body: [saved] } });
    fixture.detectChanges();

    expect(text()).toContain('Fast cross');
    expect(text()).toContain('Moving-average crossover (fast 5, slow 20)');
    expect(text()).toContain('off');
    expect(text()).toContain('never asked');
    expect(text()).toContain('last signal buy: SMA5 crossed above SMA20');

    component['toggle'](saved, true);
    const request = backend.expectOne('/api/v1/strategies/4/active');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ active: true });
    request.flush({ ...saved, active: true });
    backend.expectOne('/api/v1/strategies').flush([{ ...saved, active: true }]);
    fixture.detectChanges();

    expect(text()).toContain('proposing drafts');
  });

  it('evaluate now says what each strategy did, and points at the draft', () => {
    answerAll({ '/api/v1/strategies': { body: [saved] } });
    fixture.detectChanges();

    button('Evaluate now')!.click();
    backend
      .expectOne('/api/v1/strategies/evaluate')
      .flush([
        {
          strategyId: 4,
          name: 'Fast cross',
          symbol: 'AAPL',
          action: 'buy',
          reason: 'SMA5 crossed above SMA20',
          orderId: 7,
          note: 'draft proposed',
        },
      ]);
    backend.expectOne('/api/v1/strategies').flush([saved]);
    fixture.detectChanges();

    expect(text()).toContain(
      'Fast cross: proposed draft #7, buy AAPL — SMA5 crossed above SMA20. Confirm it on the Markets screen.',
    );
  });

  it('a failed backtests request is a sentence, not "No backtests yet"', () => {
    answerAll({ '/api/v1/backtests': { body: null, status: 500 } });
    fixture.detectChanges();

    expect(text()).toContain('Could not load past backtests');
    expect(text()).not.toContain('No backtests yet');
  });
});
