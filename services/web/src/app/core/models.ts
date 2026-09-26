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

/** Watching the market (M7a). A price is not money: nothing here is summed into a balance. */
export interface MarketStatus {
  provider: string;
  available: boolean;
  detail: string | null;
  scheduled: boolean;
  refreshEvery: string;
  notifierConfigured: boolean;
  lastRefreshAt: string | null;
  lastRefreshOutcome: string | null;
}

export interface WatchlistEntry {
  id: number;
  securityId: number;
  symbol: string;
  name: string | null;
  note: string | null;
  createdAt: string;
}

export interface QuoteRow {
  securityId: number;
  symbol: string;
  name: string | null;
  price: number | null;
  previousClose: number | null;
  /** Today's move against the previous close, in percent. Null when unknown. */
  changePct: number | null;
  asOf: string | null;
  /** "alpaca", "fake". A fake quote is a stand-in and is labelled as such on screen. */
  source: string | null;
  watched: boolean;
  held: boolean;
}

export interface RefreshResult {
  provider: string;
  fetched: number;
  stored: number;
  warnings: string[];
  alertsFired: number;
}

export interface HoldingAtMarket {
  accountId: number;
  accountName: string;
  securityId: number;
  symbol: string;
  securityName: string | null;
  cash: boolean;
  snapshotAsOf: string;
  quantity: number | null;
  snapshotPrice: number | null;
  /** What the positions file said, on snapshotAsOf. The figure balances and net worth use. */
  snapshotValue: number;
  livePrice: number | null;
  quoteAsOf: string | null;
  quoteSource: string | null;
  /** quantity × latest quote; a moment, never the balance. Null without a quote or a quantity. */
  liveValue: number | null;
}

export type AlertRule = 'above' | 'below' | 'pct_move';

export interface PriceAlert {
  id: number;
  securityId: number;
  symbol: string;
  rule: AlertRule;
  threshold: number;
  active: boolean;
  armed: boolean;
  lastFiredAt: string | null;
  note: string | null;
}

export interface AlertEvent {
  id: number;
  alertId: number;
  symbol: string;
  rule: AlertRule;
  threshold: number;
  price: number | null;
  firedAt: string;
  message: string;
  delivered: boolean;
  deliveryError: string | null;
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

// --- orders (M7b, D-18) ---

export type OrderVenue = 'paper' | 'manual';
export type OrderSide = 'buy' | 'sell';
export type OrderType = 'market' | 'limit';
export type OrderStatus =
  | 'draft'
  | 'confirmed'
  | 'submitted'
  | 'accepted'
  | 'partially_filled'
  | 'filled'
  | 'placed_manually'
  | 'cancelled'
  | 'rejected'
  | 'expired'
  | 'failed';

/**
 * An order from proposal to fill. Nothing here is a ledger row: a fill changes what is owned, and
 * what is owned is learned from the next positions import.
 */
export interface TradeOrder {
  id: number;
  symbol: string;
  venue: OrderVenue;
  side: OrderSide;
  quantity: number;
  orderType: OrderType;
  limitPrice: number | null;
  timeInForce: 'day' | 'gtc';
  status: OrderStatus;
  proposedBy: 'person' | 'assistant';
  rationale: string | null;
  /** The quote the draft was sized against, when it was a market order. */
  referencePrice: number | null;
  /** quantity × (limit price or reference price): what the cap measures and the person confirms. */
  notionalEstimate: number;
  broker: string | null;
  brokerOrderId: string | null;
  brokerStatus: string | null;
  filledQuantity: number;
  filledAvgPrice: number | null;
  createdAt: string;
  confirmedAt: string | null;
  submittedAt: string | null;
  filledAt: string | null;
  closedAt: string | null;
  lastError: string | null;
  /** "buy 2 AAPL at market (paper)" — the sentence a confirmation restates. */
  description: string;
}

export interface ProposeOrder {
  symbol: string;
  venue: OrderVenue;
  side: OrderSide;
  /** Strings all the way to the server, like every amount in this app. */
  quantity: string;
  orderType: OrderType;
  limitPrice: string | null;
  timeInForce: 'day' | 'gtc';
  proposedBy: 'person';
  rationale: string | null;
}

/** The echo that confirms a draft. It must be the draft's own symbol, side, quantity and limit. */
export interface ConfirmOrder {
  symbol: string;
  side: OrderSide;
  quantity: string;
  limitPrice: string | null;
}

export interface OrderEvent {
  id: number;
  at: string;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  actor: 'person' | 'assistant' | 'system' | 'broker';
  note: string | null;
}

export interface TradingStatus {
  /** The kill switch, TRADING_ENABLED. Off means a confirmed paper order is kept, not sent. */
  enabled: boolean;
  dailyNotionalCap: number;
  usedToday: number;
  remainingToday: number;
  broker: {
    broker: string;
    available: boolean;
    /** Always true from this release's brokers; shown as an alarm if it is ever false. */
    paper: boolean;
    marketOpen: boolean | null;
    buyingPower: number | null;
    portfolioValue: number | null;
    detail: string | null;
  };
}

// --- strategies and backtests (M7c, D-19) ---

export type Timeframe = '1Min' | '5Min' | '15Min' | '1Hour' | '1Day';
export const TIMEFRAMES: Timeframe[] = ['1Min', '5Min', '15Min', '1Hour', '1Day'];

export interface StrategyParamSpec {
  name: string;
  label: string;
  type: 'int' | 'decimal';
  description: string;
  /** Decimals as strings, as everywhere. */
  default: string;
  min: string | null;
  max: string | null;
}

export interface StrategyInfo {
  kind: string;
  label: string;
  description: string;
  /** Needs intraday bars; refuses a daily timeframe. */
  intraday: boolean;
  params: StrategyParamSpec[];
}

/** A saved rule. Active means it proposes drafts on a timer; it never trades by itself. */
export interface SavedStrategy {
  id: number;
  name: string;
  kind: string;
  params: Record<string, string>;
  symbol: string;
  timeframe: Timeframe;
  quantity: number;
  active: boolean;
  notes: string | null;
  lastEvaluatedAt: string | null;
  lastEvaluation: string | null;
  lastSignalAt: string | null;
  lastSignal: string | null;
  createdAt: string;
}

export interface SaveStrategy {
  name: string;
  kind: string;
  params: Record<string, string>;
  symbol: string;
  timeframe: Timeframe;
  quantity: string;
  notes: string | null;
}

/** The Python service's metrics, decimals as strings; sharpe is the one float. */
export interface BacktestMetrics {
  bars: number;
  trades: number;
  total_return_pct: string;
  benchmark_return_pct: string;
  max_drawdown_pct: string;
  win_rate_pct: string | null;
  profit_factor: string | null;
  avg_trade_pct: string | null;
  exposure_pct: string;
  sharpe: number | null;
  final_equity: string;
  day_trades: number;
}

export interface BacktestTradeRow {
  entered_at: string;
  exited_at: string | null;
  quantity: number;
  entry_price: string;
  exit_price: string | null;
  pnl: string | null;
  return_pct: string | null;
  reason_in: string;
  reason_out: string | null;
  same_day: boolean;
}

export interface BacktestResultBody {
  provider: string;
  strategy: string;
  params: Record<string, string>;
  symbol: string;
  timeframe: Timeframe;
  start: string;
  end: string;
  metrics: BacktestMetrics;
  in_sample: BacktestMetrics | null;
  out_of_sample: BacktestMetrics | null;
  equity_curve: { ts: string; equity: string }[];
  trades: BacktestTradeRow[];
  /** The honesty notes. Shown before any number. */
  warnings: string[];
}

export interface BacktestRun {
  id: number;
  strategyId: number | null;
  kind: string;
  params: Record<string, string>;
  symbol: string;
  timeframe: Timeframe;
  start: string;
  end: string;
  initialCash: number;
  slippageBps: number;
  commission: number;
  outOfSampleFraction: number;
  provider: string;
  bars: number;
  trades: number;
  dayTrades: number;
  totalReturnPct: number;
  benchmarkReturnPct: number;
  maxDrawdownPct: number;
  sharpe: number | null;
  finalEquity: number;
  inSampleReturnPct: number | null;
  outOfSampleReturnPct: number | null;
  warnings: string[];
  createdAt: string;
  /** Only on a single run; the list carries null. */
  result: BacktestResultBody | null;
}

export interface RunBacktest {
  kind: string;
  params: Record<string, string>;
  symbol: string;
  timeframe: Timeframe;
  start: string;
  end: string;
  initialCash: string;
  slippageBps: string;
  commission: string;
  outOfSampleFraction: string;
  strategyId: number | null;
}

export interface StrategyOutcome {
  strategyId: number;
  name: string;
  symbol: string;
  action: string | null;
  reason: string | null;
  orderId: number | null;
  note: string;
}

// --- the app speaks up (M8, D-20) ---

export interface DigestItem {
  kind:
    'reconciliation' | 'order' | 'reminder' | 'budget' | 'stale' | 'alerts' | 'strategy' | string;
  severity: 'high' | 'medium' | 'low';
  /** A sentence with the number in it. The push and the dashboard show the same one. */
  text: string;
  link: string;
}

export interface DigestRun {
  id: number;
  forDate: string;
  ranAt: string;
  manual: boolean;
  items: number;
  sent: boolean;
  deliveryError: string | null;
  body: string;
}

export interface DigestSettings {
  digestEnabled: boolean;
  /** HH:mm in the app's zone. */
  digestTime: string;
  staleAfterDays: number;
  draftWaitHours: number;
  quietWhenEmpty: boolean;
}

export type ReminderCadence = 'once' | 'weekly' | 'monthly' | 'quarterly' | 'yearly';

export interface Reminder {
  id: number;
  title: string;
  notes: string | null;
  dueOn: string;
  cadence: ReminderCadence;
  leadDays: number;
  amount: number | null;
  active: boolean;
  lastDoneOn: string | null;
}

export interface NewReminder {
  title: string;
  notes: string | null;
  dueOn: string;
  cadence: ReminderCadence;
  leadDays: number;
  /** A string, like every amount; null when there is none. */
  amount: string | null;
}

// --- cash flow and net worth history (M9, D-21) ---

export interface NetWorthPoint {
  asOf: string;
  /** Null on the combined row. */
  ledgerEntityId: number | null;
  netWorth: number;
  snapshotAccounts: number;
}

export type RecurringStatus = 'upcoming' | 'on track' | 'missing';

/** A recurring charge found in the ledger, with its evidence. */
export interface RecurringSeries {
  key: string;
  label: string;
  accountName: string;
  direction: Direction;
  cadence: 'weekly' | 'biweekly' | 'monthly' | 'quarterly' | 'yearly';
  typicalAmount: number;
  amountVaries: boolean;
  lastAmount: number;
  lastDate: string;
  nextExpected: string | null;
  occurrences: number;
  status: RecurringStatus;
}

export interface ExpectedCharge {
  date: string;
  label: string;
  accountName: string;
  direction: Direction;
  amount: number;
}

export interface CashflowAnomaly {
  kind: 'duplicate' | 'unusual_amount' | string;
  severity: 'high' | 'medium' | 'low';
  text: string;
  date: string;
}

export interface CashflowReport {
  series: RecurringSeries[];
  upcoming: ExpectedCharge[];
  anomalies: CashflowAnomaly[];
  expectedOut: number;
  expectedIn: number;
  days: number;
}
