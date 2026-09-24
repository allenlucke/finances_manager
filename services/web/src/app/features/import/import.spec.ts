import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ImportResult } from '../../core/models';
import { ImportComponent, failureMessage } from './import';

/**
 * The import screen is where the parser's explanations finally reach a person. They were counted
 * into a log line and dropped for two weeks, so the assertions here read the DOM: a note nobody
 * renders is not a note.
 */
describe('failureMessage', () => {
  it('shows the reason the API wrote, whatever status carried it', () => {
    // "Unknown account" arrives as a 400 and used to be replaced by the fallback.
    expect(failureMessage({ status: 400, error: { detail: 'Unknown account' } })).toBe(
      'Unknown account',
    );
    expect(
      failureMessage({
        status: 422,
        error: { detail: 'No parser matches this file. Known formats: chase_card, cacu' },
      }),
    ).toContain('No parser matches this file');
  });

  it('never claims nothing was saved for a status it cannot interpret', () => {
    // A gateway timeout mid-import has no body, and by then the batch may have committed.
    for (const status of [0, 502, 504, undefined]) {
      const message = failureMessage({ status });
      expect(message, `status ${status}`).not.toContain('Nothing was saved');
      expect(message, `status ${status}`).toContain('import history');
    }
  });

  it('has a plain sentence for a refusal that carried no reason', () => {
    expect(failureMessage({ status: 422 })).toContain('could not be read');
    expect(failureMessage({ status: 422, error: { detail: '  ' } })).toContain('could not be read');
  });
});

describe('ImportComponent', () => {
  let backend: HttpTestingController;
  let component: Record<string, any>;
  let fixture: ReturnType<typeof TestBed.createComponent<ImportComponent>>;

  const applied: ImportResult = {
    id: 1,
    accountId: 1,
    filename: 'statement.csv',
    status: 'applied',
    rowCount: 3,
    appliedCount: 2,
    duplicateCount: 0,
    error: null,
    startedAt: '2026-09-12T00:00:00Z',
    completedAt: '2026-09-12T00:00:01Z',
    unlinkedAccounts: [],
    warnings: [],
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
    fixture = TestBed.createComponent(ImportComponent);
    component = fixture.componentInstance as unknown as Record<string, any>;
    backend = TestBed.inject(HttpTestingController);
    backend.match('/api/v1/accounts').forEach((r) => r.flush([]));
    backend.match('/api/v1/categories').forEach((r) => r.flush([]));
    backend.match('/api/v1/entities').forEach((r) => r.flush([]));
    backend.match('/api/v1/imports').forEach((r) => r.flush([]));
    backend
      .match((r) => r.url.startsWith('/api/v1/transactions'))
      .forEach((r) =>
        r.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 }),
      );
  });

  afterEach(() => backend.verify());

  it('renders every note the parser wrote about the file, as text', () => {
    component['lastResult'].set({
      ...applied,
      warnings: ["line 3: Unrecognized date 'nope'", 'File was not UTF-8; read as Windows-1252.'],
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('2 note(s) from reading the file');
    expect(text).toContain("line 3: Unrecognized date 'nope'");
    expect(text).toContain('Windows-1252');
  });

  it('says nothing about notes when there were none', () => {
    component['lastResult'].set(applied);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('3 row(s) read');
    expect(text).not.toContain('note(s) from reading the file');
  });

  it('describes a positions result in its own words, never as duplicates', () => {
    component['lastKind'].set('positions');
    component['lastResult'].set({ ...applied, rowCount: 9, appliedCount: 0, duplicateCount: 9 });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('9 updated');
    expect(text).not.toContain('already present');
  });

  it('sends no account at all when none is chosen', () => {
    // An id of 0 for "none" was a 400 the person could not read; an export that names its own
    // accounts, and the first import into an empty install, both have none to give.
    component['file'].set(new File(['Date,Description,Amount\n'], 'x.csv'));
    component['form'].setValue({ accountId: null });
    component['upload']();

    const request = backend.expectOne('/api/v1/imports');
    expect((request.request.body as FormData).has('accountId')).toBe(false);
    request.flush({ ...applied, unlinkedAccounts: [] });
    backend.match(() => true).forEach((r) => r.flush([]));
  });

  it('a failed accounts request is a sentence, not an empty select', () => {
    // Reset the flushed-in-beforeEach state by creating a fresh component whose accounts fail.
    const failing = TestBed.createComponent(ImportComponent);
    backend
      .match('/api/v1/accounts')
      .forEach((r) => r.flush(null, { status: 500, statusText: 'Error' }));
    backend.match('/api/v1/categories').forEach((r) => r.flush([]));
    backend.match('/api/v1/entities').forEach((r) => r.flush([]));
    backend.match('/api/v1/imports').forEach((r) => r.flush([]));
    backend
      .match((r) => r.url.startsWith('/api/v1/transactions'))
      .forEach((r) =>
        r.flush({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 }),
      );
    failing.detectChanges();

    expect((failing.nativeElement as HTMLElement).textContent).toContain('Could not load accounts');
  });

  it("shows the file's own word for a row waiting in the review queue", () => {
    // A "Return" beside a credit says which category to refund; the API kept the hint and threw
    // the word away until V9.
    component['reviewPage'].value.set({
      content: [
        {
          id: 1,
          accountId: 1,
          categoryId: null,
          transactionDate: '2026-08-07',
          amount: 12,
          direction: 'credit',
          signedAmount: 12,
          description: 'KROGER #4521',
          merchant: 'KROGER',
          transfer: false,
          transferAccountId: null,
          transferGroupId: null,
          source: 'file_import',
          sourceType: 'Return',
        },
        {
          id: 2,
          accountId: 1,
          categoryId: null,
          transactionDate: '2026-08-08',
          amount: 5,
          direction: 'debit',
          signedAmount: -5,
          description: 'COFFEE',
          merchant: 'COFFEE',
          transfer: false,
          transferAccountId: null,
          transferGroupId: null,
          source: 'manual',
          sourceType: null,
        },
      ],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 100,
    });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('file says Return');
    expect(text.match(/file says/g)).toHaveLength(1);
  });

  it('keeps the notes with the import in the history', () => {
    component['batches'].value.set([{ ...applied, warnings: ['line 9: Empty amount'] }]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('1 note(s) from the parser');
    expect(text).toContain('line 9: Empty amount');
  });
});
