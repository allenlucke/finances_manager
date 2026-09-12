import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TransactionsComponent } from './transactions';

/** The accounts and categories requests, which used to have no error branch at all. */
describe('TransactionsComponent reference data', () => {
  let backend: HttpTestingController;
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
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('a failed accounts request is an error on screen, not an install with no accounts', () => {
    backend.expectOne('/api/v1/accounts').flush(null, { status: 500, statusText: 'Error' });
    backend.expectOne('/api/v1/categories').flush([]);
    backend
      .expectOne((r) => r.url.startsWith('/api/v1/transactions'))
      .flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 200 });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Could not load accounts');
    expect(text).not.toContain('No accounts yet');
  });
});
