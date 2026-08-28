import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Account,
  Category,
  CategoryKind,
  CreateTransaction,
  ImportResult,
  LedgerEntity,
  Me,
  NetWorthRow,
  Page,
  ReconciliationRow,
  SpendRow,
  Target,
  Transaction,
} from './models';

/**
 * The single place this app talks to the backend.
 *
 * Everything goes through the Java API — the browser never calls the Python AI service directly.
 * See docs/ARCHITECTURE.md.
 */
@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  // --- auth ---

  /**
   * Fetches a CSRF token, which also establishes the session cookie.
   * Must be called before the first mutating request, including login itself.
   */
  primeCsrf(): Observable<{ headerName: string; token: string }> {
    return this.http.get<{ headerName: string; token: string }>(`${this.base}/auth/csrf`);
  }

  setupRequired(): Observable<{ required: boolean }> {
    return this.http.get<{ required: boolean }>(`${this.base}/setup`);
  }

  createFirstUser(email: string, displayName: string, password: string): Observable<Me> {
    return this.http.post<Me>(`${this.base}/setup`, { email, displayName, password });
  }

  login(username: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/login`, { username, password });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/logout`, {});
  }

  me(): Observable<Me> {
    return this.http.get<Me>(`${this.base}/auth/me`);
  }

  // --- reference data ---

  entities(): Observable<LedgerEntity[]> {
    return this.http.get<LedgerEntity[]>(`${this.base}/entities`);
  }

  accounts(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.base}/accounts`);
  }

  createAccount(body: {
    name: string;
    accountType: string;
    ledgerEntityId: number;
    mask?: string | null;
    externalId?: string | null;
  }): Observable<Account> {
    return this.http.post<Account>(`${this.base}/accounts`, body);
  }

  categories(kind?: CategoryKind): Observable<Category[]> {
    const params = kind ? new HttpParams().set('kind', kind) : undefined;
    return this.http.get<Category[]>(`${this.base}/categories`, { params });
  }

  createCategory(body: { name: string; kind: CategoryKind; parentId?: number | null }) {
    return this.http.post<Category>(`${this.base}/categories`, body);
  }

  targets(): Observable<Target[]> {
    return this.http.get<Target[]>(`${this.base}/targets`);
  }

  /** Setting a target closes the previous one; the server handles that. */
  setTarget(body: {
    categoryId: number;
    ledgerEntityId: number;
    amount: string;
    effectiveFrom?: string;
    note?: string | null;
  }): Observable<Target> {
    return this.http.post<Target>(`${this.base}/targets`, body);
  }

  // --- transactions ---

  transactions(from: string, to: string, page = 0, size = 50): Observable<Page<Transaction>> {
    const params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('page', page)
      .set('size', size);
    return this.http.get<Page<Transaction>>(`${this.base}/transactions`, { params });
  }

  needsReview(page = 0, size = 50): Observable<Page<Transaction>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Transaction>>(`${this.base}/transactions/review`, { params });
  }

  /** Returns every leg written: one for a normal entry, two for a transfer. */
  createTransaction(body: CreateTransaction): Observable<Transaction[]> {
    return this.http.post<Transaction[]>(`${this.base}/transactions`, body);
  }

  categorize(id: number, categoryId: number | null): Observable<Transaction> {
    return this.http.put<Transaction>(`${this.base}/transactions/${id}/category`, { categoryId });
  }

  /** Soft delete. Removes every leg of a transfer, not just this one. */
  deleteTransaction(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/transactions/${id}`);
  }

  // --- imports ---

  /**
   * Uploads a statement. Safe to repeat: rows already present come back as duplicates rather than
   * being added again.
   *
   * <p>Sent as multipart, so no Content-Type is set here — the browser must add its own boundary,
   * and setting the header by hand produces a request the server cannot parse.
   */
  importStatement(accountId: number, file: File): Observable<ImportResult> {
    const form = new FormData();
    form.append('accountId', String(accountId));
    form.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/imports`, form);
  }

  imports(): Observable<ImportResult[]> {
    return this.http.get<ImportResult[]>(`${this.base}/imports`);
  }

  // --- reports ---

  netWorth(): Observable<NetWorthRow[]> {
    return this.http.get<NetWorthRow[]>(`${this.base}/reports/net-worth`);
  }

  spendVsTarget(from?: string, to?: string): Observable<SpendRow[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<SpendRow[]>(`${this.base}/reports/spend-vs-target`, { params });
  }

  reconciliation(): Observable<ReconciliationRow[]> {
    return this.http.get<ReconciliationRow[]>(`${this.base}/reports/reconciliation`);
  }
}
