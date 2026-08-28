import { amountClass, isoDate, money, monthLabel } from './money';

describe('money formatting', () => {
  it('always shows the sign, so colour is never the only cue', () => {
    expect(money(-696.31)).toContain('-');
    expect(money(-696.31)).toContain('696.31');
    expect(money(2303.69)).toBe('$2,303.69');
  });

  it('renders a missing value as a dash rather than $0.00', () => {
    // A blank target and a zero target mean different things; conflating them would mislead.
    expect(money(null)).toBe('—');
    expect(money(undefined)).toBe('—');
    expect(money(0)).toBe('$0.00');
  });

  it('classes negative and positive amounts differently, and zero as neither', () => {
    expect(amountClass(-1)).toContain('amount--negative');
    expect(amountClass(1)).toContain('amount--positive');
    expect(amountClass(0)).toBe('amount');
    expect(amountClass(null)).toBe('amount');
  });

  it('labels a month without shifting it across a timezone boundary', () => {
    // Parsing "2026-08-01" as UTC then formatting locally can land in July. It must not.
    expect(monthLabel('2026-08-01')).toBe('August 2026');
    expect(monthLabel('2026-01-01')).toBe('January 2026');
  });

  it('returns nothing at all for a date it cannot read', () => {
    // Not cosmetic. toLocaleDateString answers an unparseable date with the *string* "Invalid
    // Date" — truthy, so every `|| fallback` guarding these call sites was dead code, and a brand
    // new account saw "Invalid Date" as the first thing on its dashboard.
    for (const notADate of ['', '   ', 'August', '2026', '2026-13-01', '2026-00-01', 'nope']) {
      expect(monthLabel(notADate)).toBe('');
    }
  });

  it('formats a Date as a local calendar date', () => {
    expect(isoDate(new Date(2026, 7, 14))).toBe('2026-08-14');
    expect(isoDate(new Date(2026, 0, 1))).toBe('2026-01-01');
  });
});
