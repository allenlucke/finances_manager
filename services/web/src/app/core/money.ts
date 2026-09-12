/** Formatting helpers. Deliberately no arithmetic — see the note in models.ts. */

const CURRENCY = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/**
 * `-$696.31`. The sign is always shown, so colour is never the only cue.
 *
 * <p>Negative zero is zero. `Intl` formats `-0` as `-$0.00`, and `money(-row.netAmount)` on an
 * income row that netted to nothing printed exactly the string the QA brief tells a tester to hunt
 * for. `-0 === 0` is true, which is what the comparison relies on.
 */
export function money(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return CURRENCY.format(value === 0 ? 0 : value);
}

/** Class name carrying the sign, paired with the leading "-" rather than replacing it. */
export function amountClass(value: number | null | undefined): string {
  if (value === null || value === undefined || value === 0) {
    return 'amount';
  }
  return value < 0 ? 'amount amount--negative' : 'amount amount--positive';
}

/**
 * "August 2026" from an ISO date, or an empty string if it is not one.
 *
 * <p>The empty return is the point. `toLocaleDateString` on an unparseable date does not throw and
 * does not return nothing — it returns the literal text "Invalid Date", which is a truthy string, so
 * it sails through a `?? ''` upstream and a `|| 'fallback'` downstream and lands in front of the
 * user. That is exactly how it reached the dashboard: an account with no transactions yet rendered
 * "Invalid Date" as the first thing a new user saw.
 */
export function monthLabel(iso: string): string {
  const [year, month] = (iso ?? '').split('-').map(Number);
  if (!Number.isFinite(year) || !Number.isFinite(month) || month < 1 || month > 12) {
    return '';
  }
  return new Date(year, month - 1, 1).toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric',
  });
}

/**
 * ISO yyyy-mm-dd for a Date, in local time — these are calendar dates, not instants.
 *
 * <p>Returns null for anything that is not a real date. A datepicker writes `null` into its
 * control when the text is cleared and an `Invalid Date` when it is mistyped; the previous
 * version threw on the first (inside a `valueChanges` subscription, which tore the subscription
 * down for good and left a spinner running forever) and produced `NaN-NaN-NaN` on the second.
 */
export function isoDate(date: Date | null | undefined): string | null {
  if (!(date instanceof Date) || !Number.isFinite(date.getTime())) {
    return null;
  }
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * The two clock reads take an optional `now`, so a component can hold the moment it loaded in a
 * signal and re-evaluate on refresh — a `computed` over a bare `new Date()` evaluates once and a
 * dashboard left open across a month boundary kept showing the old month.
 */
export function firstOfThisMonth(now: Date = new Date()): string {
  return isoDate(new Date(now.getFullYear(), now.getMonth(), 1))!;
}

export function today(now: Date = new Date()): string {
  return isoDate(now)!;
}

/** "Aug 27, 2026" from an ISO date, or the raw string when it is not one. Dates only, no time. */
export function shortDate(iso: string): string {
  const parsed = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) {
    return iso;
  }
  return parsed.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}
