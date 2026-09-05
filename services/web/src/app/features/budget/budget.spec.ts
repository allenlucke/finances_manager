import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { BudgetComponent } from './budget';

describe('BudgetComponent', () => {
  let backend: HttpTestingController;
  let component: Record<string, any>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
      ],
    });
    component = TestBed.createComponent(BudgetComponent).componentInstance as unknown as Record<
      string,
      any
    >;
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('keeps income out of the spending table', () => {
    // The view flips sign so that "spent" is positive, which makes a paycheque arrive negative.
    // Unfiltered, it rendered Spent -$3,000.00 / Remaining $6,000.00 under "Spending by month".
    backend.expectOne('/api/v1/entities').flush([]);
    backend.expectOne('/api/v1/categories').flush([]);
    backend.expectOne('/api/v1/targets').flush([]);
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/reports/spend-vs-target'))
      .flush([
        {
          month: '2026-08-01',
          categoryId: 1,
          categoryName: 'Groceries',
          categoryKind: 'expense',
          ledgerEntityId: 1,
          netAmount: 150,
          targetAmount: 400,
          remaining: 250,
          transactionCount: 3,
        },
        {
          month: '2026-08-01',
          categoryId: 2,
          categoryName: 'Salary',
          categoryKind: 'income',
          ledgerEntityId: 1,
          netAmount: -3000,
          targetAmount: 3000,
          remaining: 6000,
          transactionCount: 1,
        },
      ]);

    expect(component['recentSpend']().map((r: { categoryName: string }) => r.categoryName)).toEqual(
      ['Groceries'],
    );
    expect(
      component['recentIncome']().map((r: { categoryName: string }) => r.categoryName),
    ).toEqual(['Salary']);
  });

  it('sends the chosen start date and cadence rather than a hidden first-of-month', () => {
    backend
      .expectOne('/api/v1/entities')
      .flush([{ id: 1, name: 'Personal', kind: 'personal', active: true }]);
    backend
      .expectOne('/api/v1/categories')
      .flush([{ id: 5, name: 'Groceries', kind: 'expense', parentId: null, active: true }]);
    backend.expectOne('/api/v1/targets').flush([]);
    backend.expectOne((r) => r.url.startsWith('/api/v1/reports/spend-vs-target')).flush([]);

    component['targetForm'].setValue({
      categoryId: 5,
      ledgerEntityId: 1,
      amount: '1200',
      cadence: 'yearly',
      effectiveFrom: new Date(2026, 7, 15),
    });
    component['setTarget']();

    const request = backend.expectOne('/api/v1/targets');
    expect(request.request.body).toMatchObject({
      cadence: 'yearly',
      effectiveFrom: '2026-08-15',
      amount: '1200',
    });
    request.flush({ id: 1 });
    backend.match(() => true).forEach((r) => r.flush([]));
  });
});
