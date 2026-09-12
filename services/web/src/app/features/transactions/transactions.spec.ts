import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { TransactionsComponent } from './transactions';

describe('TransactionsComponent', () => {
  let backend: HttpTestingController;
  let component: Record<string, any>;
  let snackBar: MatSnackBar;
  let fixture: ReturnType<typeof TestBed.createComponent<TransactionsComponent>>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNativeDateAdapter(),
        provideRouter([]),
      ],
    });
    fixture = TestBed.createComponent(TransactionsComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
    snackBar = TestBed.inject(MatSnackBar);
    backend.expectOne('/api/v1/accounts').flush([]);
    backend.expectOne('/api/v1/categories').flush([]);
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/transactions'))
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 });
  });

  afterEach(() => backend.verify());

  it('a cleared date does not throw, request, or blank the ledger', () => {
    // Regression: isoDate(null) threw inside the valueChanges subscription, tearing it down for
    // good and leaving the loading bar running until a page refresh.
    component['range'].setValue({ from: null, to: new Date() }, { emitEvent: false });

    expect(() => component['reload']()).not.toThrow();
    expect(component['rangeError']()).toContain('real date');
    backend.expectNone((r) => r.url.startsWith('/api/v1/transactions'));
  });

  it('a range that ends before it starts is refused rather than sent', () => {
    component['range'].setValue(
      { from: new Date(2026, 7, 31), to: new Date(2026, 7, 1) },
      { emitEvent: false },
    );
    component['reload']();
    expect(component['rangeError']()).toContain('ends before it starts');
    backend.expectNone((r) => r.url.startsWith('/api/v1/transactions'));
  });

  it('says how many rows were left out when the page is full', () => {
    component['range'].setValue(
      { from: new Date(2026, 0, 1), to: new Date(2026, 11, 31) },
      { emitEvent: false },
    );
    component['reload']();
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/transactions'))
      .flush({
        content: [{ id: 1 }],
        totalElements: 450,
        totalPages: 3,
        number: 0,
        size: 200,
      });
    expect(component['truncated']()).toBe(true);
    expect(component['total']()).toBe(450);
  });

  it('a refused recategorization reloads so the row stops showing the refused category', () => {
    const row = { id: 9, description: 'x', transfer: true };
    component['recategorize'](row, 3);
    backend
      .expectOne('/api/v1/transactions/9/category')
      .flush(null, { status: 422, statusText: 'Unprocessable' });
    // The reload is the assertion: before, the one-way-bound select kept the refused value.
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/transactions'))
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 });
  });

  it('labels the other account by where the money went, following the direction', () => {
    // The server records the chosen direction on the Account field's account and the opposite
    // on the other. "Moved to" with "Money in" selected therefore named the account the money
    // came FROM, and a transfer entered that way moved both balances the wrong way — net worth
    // unchanged, nothing visibly broken. The label is read from the DOM, not the signal: a
    // label nobody renders is not a label.
    component['form'].patchValue({ transfer: true });
    fixture.detectChanges();
    const screen = fixture.nativeElement as HTMLElement;

    expect(screen.textContent).toContain('Moved to');
    expect(screen.textContent).not.toContain('Moved from');

    component['form'].controls.direction.setValue('credit');
    fixture.detectChanges();

    expect(screen.textContent).toContain('Moved from');
    expect(screen.textContent).not.toContain('Moved to');
  });

  it('with no accounts, Add is disabled and the screen says why', () => {
    // `Validators.required` treats a number as present, zero included, so the form used to be
    // valid with nothing to choose from: Add was enabled and the server answered 400 to an id
    // of 0, reported as "Could not save the transaction".
    fixture.detectChanges();
    const screen = fixture.nativeElement as HTMLElement;

    expect(component['form'].invalid).toBe(true);
    const add = screen.querySelector<HTMLButtonElement>('button[type="submit"]');
    expect(add?.disabled).toBe(true);
    expect(screen.textContent).toContain('No accounts yet');
  });

  it('deleting offers an undo that restores', () => {
    const action = new Subject<void>();
    const open = vi
      .spyOn(snackBar, 'open')
      .mockReturnValue({ onAction: () => action.asObservable() } as never);
    const row = { id: 4, description: 'KROGER', transferGroupId: null };

    component['remove'](row);
    backend
      .expectOne('/api/v1/transactions/4')
      .flush(null, { status: 204, statusText: 'No Content' });
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/transactions'))
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 });
    expect(open).toHaveBeenCalledWith('Transaction removed', 'Undo', expect.anything());

    action.next();
    backend.expectOne('/api/v1/transactions/4/restore').flush([{ id: 4 }]);
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/transactions'))
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 });
  });
});
