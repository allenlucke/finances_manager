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
  /** Last four digits, for recognising the account. Never the full number. */
  mask: string | null;
  /**
   * Where the balance came from. `holdings` means a brokerage snapshot's market value — correct
   * only as of `balanceAsOf`, which is why both are shown rather than hidden. `transactions` means
   * the ledger sum.
   */
  balanceSource: 'transactions' | 'holdings';
  balanceAsOf: string | null;
  /** Null unless every position has a known basis; a partial basis is not a basis. */
  costBasis: number | null;
}

/** One position in an account's latest snapshot. Market value is a magnitude, never signed. */
export interface Holding {
  id: number;
  accountId: number;
  symbol: string;
  name: string | null;
  cash: boolean;
  securityType: string;
  asOf: string;
  quantity: number | null;
  lastPrice: number | null;
  marketValue: number;
  costBasis: number | null;
  totalGainLoss: number | null;
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
  /** The file's own word for the row — "Payment", "Return", "XFER". Null for a manual entry. */
  sourceType: string | null;
}

/** Spring Data's page envelope, narrowed to the fields the UI uses. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last?: boolean;
}

export interface NetWorthRow {
  /** Null on the combined row. */
  ledgerEntityId: number | null;
  netWorth: number;
  /**
   * How many of the accounts in this figure are valued from a holdings snapshot rather than the
   * ledger, and the date of the oldest such snapshot. A brokerage balance is its last positions
   * import; a deposit made since is not in the figure until the next one. Shown beside the number
   * because a net worth without that date can be quietly wrong by whatever moved after it.
   */
  snapshotAccounts: number;
  oldestSnapshot: string | null;
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

/**
 * What moved in a month, categorized or not. Every SpendRow is categorized spend only, so a month
 * that is mostly still in the review queue looks cheap without this beside it. Transfers are
 * excluded; `moneyIn` includes refunds, which is why it is not called income.
 */
export interface MonthlyTotalsRow {
  month: string;
  /** Null on the combined row. */
  ledgerEntityId: number | null;
  moneyOut: number;
  moneyIn: number;
  uncategorizedOut: number;
  uncategorizedCount: number;
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
  /**
   * `opening_balance`: computed from the statement's own starting figure, so a difference is a
   * real discrepancy. `full_history`: computed by summing everything ever, so a difference may
   * only mean the account's early history was never imported.
   */
  baseline: 'opening_balance' | 'full_history';
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
  /**
   * What the parser could not read or had to assume: a row with an unreadable date, a file that
   * was not UTF-8, a checkpoint it would not record. Each one is a sentence for the person who
   * uploaded the file. Empty is the normal case.
   */
  warnings: string[];
}

export interface UnlinkedAccount {
  /** Opaque stable link derived from the account number. Never the number itself. */
  key: string;
  mask: string | null;
  name: string | null;
  transactionCount: number;
}
