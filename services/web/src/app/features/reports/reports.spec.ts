import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ReportsComponent } from './reports';

/** The review queue is named before the totals, transfers are absent, and a failure is a sentence. */
describe('ReportsComponent', () => {
  let backend: HttpTestingController;
  let fixture: ReturnType<typeof TestBed.createComponent<ReportsComponent>>;
  let component: Record<string, any>;

  const text = () =>
    ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  const year = new Date().getFullYear();

  const review = {
    year,
    ledgerEntityId: 1,
    entityName: 'Personal',
    income: 3000,
    expenses: 180.5,
    net: 2819.5,
    uncategorizedOut: 45,
    uncategorizedIn: 0,
    uncategorizedCount: 1,
    categories: [
      { categoryId: 2, name: 'Groceries', kind: 'expense', amount: 180.5, count: 3 },
      { categoryId: 3, name: 'Salary', kind: 'income', amount: 3000, count: 1 },
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
    fixture = TestBed.createComponent(ReportsComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  const flushEntities = () =>
    backend
      .expectOne('/api/v1/entities')
      .flush([{ id: 1, name: 'Personal', kind: 'personal', active: true }]);
  const flushYear = (body: unknown, status = 200) =>
    backend
      .expectOne((r) => r.url === '/api/v1/reports/year')
      .flush(body as never, { status, statusText: status === 200 ? 'OK' : 'Error' });

  it('names the uncategorized before the totals, and shows income and spending by category', () => {
    flushEntities();
    flushYear(review);
    fixture.detectChanges();

    const body = text();
    expect(body).toContain('1 transaction(s) worth $45.00 out are still uncategorized');
    expect(body.indexOf('still uncategorized')).toBeLessThan(body.indexOf('$3,000.00'));
    expect(body).toContain(`Income, ${year}, Personal`);
    expect(body).toContain('-$180.50');
    expect(body).toContain('$2,819.50');
    expect(body).toContain('Groceries');
    expect(body).toContain('Transfers are not spending');
  });

  it('asks for the chosen year and set of books', () => {
    flushEntities();
    flushYear({ ...review, uncategorizedCount: 0 });
    fixture.detectChanges();
    expect(text()).not.toContain('still uncategorized');

    component['setYear'](year - 1);
    const request = backend.expectOne((r) => r.url === '/api/v1/reports/year');
    expect(request.request.params.get('year')).toBe(String(year - 1));
    expect(request.request.params.has('ledgerEntityId')).toBe(false);
    request.flush({
      ...review,
      year: year - 1,
      entityName: null,
      categories: [],
      income: 0,
      expenses: 0,
      net: 0,
      uncategorizedCount: 0,
    });
    fixture.detectChanges();

    expect(text()).toContain(`Income, ${year - 1}, all sets of books`);
    expect(text()).toContain(`No categorized spending in ${year - 1}`);
  });

  it('a failed request is a sentence, not a year of zeros', () => {
    flushEntities();
    flushYear(null, 500);
    fixture.detectChanges();

    expect(text()).toContain('Could not load the year');
    expect(text()).not.toContain('$0.00');
  });

  it('offers the year as a CSV link for the chosen scope', () => {
    flushEntities();
    flushYear(review);
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'a[download]',
    ) as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe(`/api/v1/reports/year.csv?year=${year}`);
    component['setEntity'](1);
    backend.expectOne((r) => r.url === '/api/v1/reports/year').flush(review);
    fixture.detectChanges();
    expect(link.getAttribute('href')).toBe(
      `/api/v1/reports/year.csv?year=${year}&ledgerEntityId=1`,
    );
  });
});
