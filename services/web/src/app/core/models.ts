/**
 * Wire types for the Spring Boot API.
 *
 * <p>Money arrives as a JSON number and is kept as a `number` here, which is safe only because
 * nothing in the browser does arithmetic on it — every figure the UI shows is computed in SQL or
 * Java (`BigDecimal`/`NUMERIC(19,4)`) and formatted here. If a total ever needs to be summed
 * client-side, it must not be done with `+` on these values.
 */

export type Direction = 'debit' | 'credit';
export type CategoryKind = 'expense' | 'income';
export type EntityKind = 'personal' | 'business';

export interface Me {
  id: number;
  email: string;
  displayName: string;
}

export interface LedgerEntity {
  id: number;
  name: string;
  kind: EntityKind;
  active: boolean;
}

export interface Account {
  id: number;
  name: string;
  accountType: string;
  ledgerEntityId: number;
  currency: string;
  active: boolean;
  /** Signed: negative means owed. See the sign convention in V2__core_domain.sql. */
  balance: number;
  transactionCount: number;
  lastActivity: string | null;
}

export interface Category {
  id: number;
  name: string;
  kind: CategoryKind;
  parentId: number | null;
  active: boolean;
}

export interface Target {
  id: number;
  categoryId: number;
  ledgerEntityId: number;
  amount: number;
  cadence: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  note: string | null;
}

export interface Transaction {
  id: number;
  accountId: number;
  categoryId: number | null;
  transactionDate: string;
  amount: number;
  direction: Direction;
  /** Negative for a debit. The value to sum for a balance. */
  signedAmount: number;
  description: string;
  merchant: string | null;
  transfer: boolean;
  transferAccountId: number | null;
  /** Both legs of one transfer share this. */
  transferGroupId: string | null;
  source: string;
}

/** Spring Data's page envelope, narrowed to the fields the UI uses. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface NetWorthRow {
  /** Null on the combined row. */
  ledgerEntityId: number | null;
  netWorth: number;
}

export interface SpendRow {
  month: string;
  categoryId: number;
  categoryName: string;
  categoryKind: CategoryKind;
  ledgerEntityId: number;
  netAmount: number;
  /** Null when no target is set, which is the normal case. */
  targetAmount: number | null;
  remaining: number | null;
  transactionCount: number;
}

export interface ReconciliationRow {
  statementId: number;
  accountId: number;
  periodStart: string;
  periodEnd: string;
  closingBalance: number;
  computedBalance: number;
  /** Non-zero means the ledger and the statement disagree, in dollars. */
  difference: number;
  reconciled: boolean;
}

export interface CreateTransaction {
  accountId: number;
  transactionDate: string;
  amount: string;
  direction: Direction;
  description: string;
  categoryId?: number | null;
  transfer?: boolean;
  transferAccountId?: number | null;
}

export interface ImportResult {
  id: number;
  accountId: number | null;
  filename: string | null;
  status: 'pending' | 'parsed' | 'applied' | 'failed';
  rowCount: number;
  /** Rows newly added. */
  appliedCount: number;
  /** Rows already present and skipped — a re-import is mostly these. */
  duplicateCount: number;
  error: string | null;
  startedAt: string;
  completedAt: string | null;
  /**
   * Accounts the file refers to that don't exist here yet, named by the institution. The UI offers
   * to create these — nobody should have to look up their own account digits to make an import work.
   */
  unlinkedAccounts: UnlinkedAccount[];
}

export interface UnlinkedAccount {
  /** Opaque stable link derived from the account number. Never the number itself. */
  key: string;
  mask: string | null;
  name: string | null;
  transactionCount: number;
}
