import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { today } from '../../core/money';
import { NeedsALookComponent } from './needs-a-look';

/** The dashboard's list is the push's list; both are sentences, and a failure is never "nothing". */
describe('NeedsALookComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<NeedsALookComponent>>;
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

  type Answer = { body: unknown; status?: number };
  function answerAll(overrides: Partial<Record<string, Answer>> = {}) {
    const answers: Record<string, Answer> = {
      '/api/v1/digest/preview': { body: [] },
      '/api/v1/reminders': { body: [] },
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
    fixture = TestBed.createComponent(NeedsALookComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('lists what needs a look as sentences, with the severity in words and a place to go', () => {
    answerAll({
      '/api/v1/digest/preview': {
        body: [
          {
            kind: 'reconciliation',
            severity: 'high',
            text: 'Everyday checking: the statement ending 2026-08-07 disagrees with the ledger by $150.00',
            link: '/dashboard',
          },
          {
            kind: 'budget',
            severity: 'medium',
            text: 'Groceries is $60.00 over its $100.00 target this month',
            link: '/budget',
          },
        ],
      },
    });
    fixture.detectChanges();

    expect(text()).toContain('disagrees with the ledger by $150.00');
    expect(text()).toContain('high');
    expect(text()).toContain('Groceries is $60.00 over');
    expect(element().querySelector('a[href="/budget"]')).not.toBeNull();
    expect(text()).not.toContain('Nothing needs a look');
  });

  it('says so when nothing needs a look, and a failed request is a sentence instead', () => {
    answerAll();
    fixture.detectChanges();
    expect(text()).toContain('Nothing needs a look');

    fixture = TestBed.createComponent(NeedsALookComponent);
    answerAll({ '/api/v1/digest/preview': { body: null, status: 500 } });
    fixture.detectChanges();
    expect(text()).toContain('Could not work out what needs a look');
    expect(text()).not.toContain('Nothing needs a look');
  });

  it('sending reports a channel that is not there, in the channel’s own words', () => {
    answerAll();
    fixture.detectChanges();

    button('Send to my phone')!.click();
    backend.expectOne('/api/v1/digest/send').flush({
      id: 1,
      forDate: today(),
      ranAt: '2026-09-26T12:00:00Z',
      manual: true,
      items: 0,
      sent: false,
      deliveryError: 'No notification channel is configured (NTFY_URL is blank).',
      body: 'All quiet: nothing needs a look.',
    });
    fixture.detectChanges();

    expect(element().querySelector('[role="alert"]')?.textContent).toContain('NTFY_URL');
  });

  it('adds a reminder with the amount as a string, and shows when it is due', () => {
    answerAll();
    fixture.detectChanges();
    component['form'].setValue({
      title: 'Estimated taxes',
      dueOn: '2026-10-15',
      cadence: 'quarterly',
      leadDays: '5',
      amount: '1200',
    });
    fixture.detectChanges();

    button('Add')!.click();

    const request = backend.expectOne('/api/v1/reminders');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      title: 'Estimated taxes',
      notes: null,
      dueOn: '2026-10-15',
      cadence: 'quarterly',
      leadDays: 5,
      amount: '1200',
    });
    const saved = {
      id: 9,
      title: 'Estimated taxes',
      notes: null,
      dueOn: '2026-10-15',
      cadence: 'quarterly',
      leadDays: 5,
      amount: 1200,
      active: true,
      lastDoneOn: null,
    };
    request.flush(saved);
    answerAll({ '/api/v1/reminders': { body: [saved] } });
    fixture.detectChanges();

    expect(text()).toContain('Estimated taxes');
    expect(text()).toContain('$1,200.00');
    expect(text()).toContain('every quarter');
    expect(text()).toMatch(/due in \d+ days|due today|due tomorrow|overdue by/);
  });

  it('done on a reminder posts, and a finished one reads as done', () => {
    const r = {
      id: 3,
      title: 'Pay the card',
      notes: null,
      dueOn: '2026-09-01',
      cadence: 'once',
      leadDays: 3,
      amount: null,
      active: true,
      lastDoneOn: null,
    };
    answerAll({ '/api/v1/reminders': { body: [r] } });
    fixture.detectChanges();
    expect(text()).toContain('overdue by');

    button('Done')!.click();
    backend
      .expectOne('/api/v1/reminders/3/done')
      .flush({ ...r, active: false, lastDoneOn: '2026-09-26' });
    answerAll({
      '/api/v1/reminders': { body: [{ ...r, active: false, lastDoneOn: '2026-09-26' }] },
    });
    fixture.detectChanges();

    expect(text()).toContain('Pay the card — done on 2026-09-26');
    expect(button('Done')).toBeUndefined();
  });
});
