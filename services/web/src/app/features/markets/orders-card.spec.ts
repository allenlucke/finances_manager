import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { money } from '../../core/money';
import { OrdersCardComponent } from './orders-card';

/**
 * Nothing reaches a broker without a restatement, and every refusal is a sentence on screen.
 * Both halves are asserted against the DOM and against the exact body that leaves the browser.
 */
describe('OrdersCardComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<OrdersCardComponent>>;
  let component: Record<string, any>;

  const element = () => fixture.nativeElement as HTMLElement;
  /** The page's words with the template's line breaks collapsed, as a reader would see them. */
  const text = () => (element().textContent ?? '').replace(/\s+/g, ' ');
  /** A button by its label, ignoring the icon's ligature text that precedes some of them. */
  const labelOf = (b: HTMLButtonElement) =>
    [...b.childNodes]
      .filter((n) => (n as Element).tagName !== 'MAT-ICON')
      .map((n) => n.textContent ?? '')
      .join('')
      .replace(/\s+/g, ' ')
      .trim();
  const button = (label: string) =>
    [...element().querySelectorAll('button')].find((b) => labelOf(b).startsWith(label));
  /** Clicks a submit button; the browser turns that into the form's submit, and so does the DOM here. */
  const submit = (label: string) => {
    const b = button(label)!;
    expect(b.disabled, label).toBe(false);
    b.click();
  };

  const off = {
    enabled: false,
    dailyNotionalCap: 1000,
    usedToday: 0,
    remainingToday: 1000,
    broker: {
      broker: 'none',
      available: false,
      paper: true,
      marketOpen: null,
      buyingPower: null,
      portfolioValue: null,
      detail: "TRADING_BROKER is 'none'.",
    },
  };
  const on = {
    ...off,
    enabled: true,
    usedToday: 760,
    remainingToday: 240,
    broker: { ...off.broker, broker: 'fake', available: true, marketOpen: true, detail: null },
  };

  const draft = {
    id: 7,
    symbol: 'AAPL',
    venue: 'paper',
    side: 'buy',
    quantity: 2,
    orderType: 'market',
    limitPrice: null,
    timeInForce: 'day',
    status: 'draft',
    proposedBy: 'assistant',
    rationale: 'a dip',
    referencePrice: 190,
    notionalEstimate: 380,
    broker: null,
    brokerOrderId: null,
    brokerStatus: null,
    filledQuantity: 0,
    filledAvgPrice: null,
    createdAt: '2026-09-26T14:00:00Z',
    confirmedAt: null,
    submittedAt: null,
    filledAt: null,
    closedAt: null,
    lastError: null,
    description: 'buy 2 AAPL at market (paper)',
  };

  type Answer = { body: unknown; status?: number };
  function answerAll(overrides: Partial<Record<string, Answer>> = {}) {
    const answers: Record<string, Answer> = {
      '/api/v1/orders/status': { body: off },
      '/api/v1/orders': { body: [] },
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
    fixture = TestBed.createComponent(OrdersCardComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('says trading is off, names the switch, and still lists the draft with its size', () => {
    answerAll({ '/api/v1/orders': { body: [draft] } });
    fixture.detectChanges();

    expect(text()).toContain('Trading is off');
    expect(text()).toContain('TRADING_ENABLED');
    expect(text()).toContain('buy 2 AAPL at market (paper)');
    expect(text()).toContain(money(380));
    expect(text()).toContain('proposed by the assistant');
    expect(text()).toContain('a dip');
    expect(button('Check with broker')?.disabled).toBe(true);
  });

  it('with trading on, shows the cap, what today has used, and what is left', () => {
    answerAll({ '/api/v1/orders/status': { body: on } });
    fixture.detectChanges();

    expect(text()).toContain('Trading is on');
    expect(text()).toContain(`paper cap ${money(1000)} a day`);
    expect(text()).toContain(`${money(760)} sent today`);
    expect(text()).toContain(`${money(240)} left`);
    expect(text()).toContain('market open');
    expect(button('Check with broker')?.disabled).toBe(false);
  });

  it('confirming needs the symbol typed back, then sends the draft’s own numbers as strings', () => {
    answerAll({ '/api/v1/orders/status': { body: on }, '/api/v1/orders': { body: [draft] } });
    fixture.detectChanges();

    button('Confirm')!.click();
    fixture.detectChanges();
    expect(text()).toContain('Type AAPL to confirm');
    expect(text()).toContain('goes to the paper broker as soon as you confirm');
    const send = button('Confirm buy')!;
    expect(send.disabled).toBe(true);

    component['echo'].setValue('aapl');
    fixture.detectChanges();
    expect(send.disabled).toBe(false);
    submit('Confirm buy');

    const request = backend.expectOne('/api/v1/orders/7/confirm');
    expect(request.request.body).toEqual({
      symbol: 'AAPL',
      side: 'buy',
      quantity: '2',
      limitPrice: null,
      actor: 'person',
    });
    request.flush({ ...draft, status: 'accepted', broker: 'fake', brokerOrderId: 'b-1' });
    answerAll({
      '/api/v1/orders/status': { body: on },
      '/api/v1/orders': { body: [{ ...draft, status: 'accepted', broker: 'fake' }] },
    });
    fixture.detectChanges();

    expect(text()).toContain('accepted by the broker');
    expect(text()).toContain('fake broker');
    expect(text()).not.toContain('Type AAPL to confirm');
  });

  it('a refusal is the server’s sentence, on screen, and the draft is reloaded', () => {
    answerAll({ '/api/v1/orders/status': { body: on }, '/api/v1/orders': { body: [draft] } });
    fixture.detectChanges();
    button('Confirm')!.click();
    component['echo'].setValue('AAPL');
    fixture.detectChanges();
    submit('Confirm buy');

    backend.expectOne('/api/v1/orders/7/confirm').flush(
      {
        detail:
          'Over the daily cap: 760 already sent today, this order adds 380, and the cap is 1000.',
      },
      { status: 422, statusText: 'Unprocessable' },
    );
    answerAll({
      '/api/v1/orders/status': { body: on },
      '/api/v1/orders': { body: [{ ...draft, status: 'cancelled' }] },
    });
    fixture.detectChanges();

    expect(text()).toContain('Over the daily cap');
    expect(element().querySelector('[role="alert"]')?.textContent).toContain('760');
    expect(text()).toContain('cancelled');
  });

  it('a manual ticket is confirmed without a broker, then marked placed with its fill', () => {
    const ticket = {
      ...draft,
      id: 9,
      venue: 'manual',
      side: 'sell',
      quantity: 3,
      orderType: 'limit',
      limitPrice: 195,
      status: 'confirmed',
      notionalEstimate: 585,
      description: 'sell 3 AAPL at a limit of 195 (manual)',
    };
    answerAll({ '/api/v1/orders': { body: [ticket] } });
    fixture.detectChanges();

    expect(text()).toContain('confirmed — take it to Fidelity');
    expect(button('Confirm')).toBeUndefined();
    button('Mark placed')!.click();
    fixture.detectChanges();
    expect(text()).toContain('Placed sell 3 AAPL at a limit of 195 (manual) at Fidelity?');
    component['fillPrice'].setValue('195.10');
    fixture.detectChanges();
    submit('Placed at Fidelity');

    const request = backend.expectOne('/api/v1/orders/9/placed');
    expect(request.request.body).toEqual({ fillPrice: '195.10', actor: 'person' });
    request.flush({ ...ticket, status: 'placed_manually', filledAvgPrice: 195.1 });
    answerAll({
      '/api/v1/orders': {
        body: [{ ...ticket, status: 'placed_manually', filledAvgPrice: 195.1 }],
      },
    });
    fixture.detectChanges();

    expect(text()).toContain(`placed at Fidelity at ${money(195.1)}`);
  });

  it('a draft is sent as strings, with the venue and reason, and never as the assistant', () => {
    answerAll();
    fixture.detectChanges();

    component['proposeForm'].setValue({
      symbol: 'msft',
      side: 'sell',
      quantity: '1.5',
      orderType: 'limit',
      limitPrice: '410.25',
      venue: 'manual',
      rationale: 'trim',
    });
    fixture.detectChanges();
    button('Save draft')!.click();

    const request = backend.expectOne('/api/v1/orders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      symbol: 'MSFT',
      venue: 'manual',
      side: 'sell',
      quantity: '1.5',
      orderType: 'limit',
      limitPrice: '410.25',
      timeInForce: 'day',
      proposedBy: 'person',
      rationale: 'trim',
    });
    request.flush({ ...draft, id: 11, description: 'sell 1.5 MSFT at a limit of 410.25 (manual)' });
    answerAll();
  });

  it('a limit order without a price cannot be saved', () => {
    answerAll();
    fixture.detectChanges();

    component['proposeForm'].setValue({
      symbol: 'MSFT',
      side: 'buy',
      quantity: '1',
      orderType: 'limit',
      limitPrice: '',
      venue: 'paper',
      rationale: '',
    });
    fixture.detectChanges();

    expect(button('Save draft')?.disabled).toBe(true);
  });

  it('the history names each actor and step', () => {
    answerAll({
      '/api/v1/orders': { body: [{ ...draft, status: 'filled', filledAvgPrice: 189.95 }] },
    });
    fixture.detectChanges();

    button('History')!.click();
    backend.expectOne('/api/v1/orders/7/events').flush([
      {
        id: 1,
        at: '2026-09-26T14:00:00Z',
        fromStatus: null,
        toStatus: 'draft',
        actor: 'assistant',
        note: 'a dip',
      },
      {
        id: 2,
        at: '2026-09-26T14:05:00Z',
        fromStatus: 'draft',
        toStatus: 'confirmed',
        actor: 'person',
        note: null,
      },
      {
        id: 3,
        at: '2026-09-26T14:05:01Z',
        fromStatus: 'confirmed',
        toStatus: 'accepted',
        actor: 'broker',
        note: 'new',
      },
      {
        id: 4,
        at: '2026-09-26T14:06:00Z',
        fromStatus: 'accepted',
        toStatus: 'filled',
        actor: 'broker',
        note: 'filled',
      },
    ]);
    fixture.detectChanges();

    expect(text()).toContain(`filled at ${money(189.95)}`);
    expect(text()).toContain('the assistant: draft');
    expect(text()).toContain('the person: draft → confirmed');
    expect(text()).toContain('the broker: accepted → filled');
  });

  it('a failed orders request is a sentence, not "No orders yet"', () => {
    answerAll({ '/api/v1/orders': { body: null, status: 500 } });
    fixture.detectChanges();

    expect(text()).toContain('Could not load orders');
    expect(text()).not.toContain('No orders yet');
  });

  it('a broker that is not paper is an alarm in text', () => {
    answerAll({
      '/api/v1/orders/status': { body: { ...on, broker: { ...on.broker, paper: false } } },
    });
    fixture.detectChanges();

    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      'This broker is not a paper account',
    );
  });
});
