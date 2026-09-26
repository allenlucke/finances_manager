"""Shared test setup.

The account key is an HMAC under a per-install secret (docs/SECURITY.md). The suite sets a
throwaway one so the readers can derive keys; the assertions that matter are that a key is stable
under one secret and different under another — see test_common.
"""

import os

os.environ.setdefault("ACCOUNT_KEY_SECRET", "test-secret-not-for-any-real-install")


def bars_from(closes, timeframe="1Day", day=None):
    """Daily bars, one per weekday from ``day``, with the given closes; open is the previous close.

    Shared by the strategy and backtest tests: a handcrafted series is how a signal or a fill is
    pinned to an exact bar.
    """
    from datetime import date, timedelta
    from decimal import Decimal

    from finances_ai.market import session_bars
    from finances_ai.models import Bar

    out = []
    previous = Decimal(str(closes[0]))
    current = day or date(2026, 9, 21)
    for close in closes:
        while current.weekday() >= 5:
            current += timedelta(days=1)
        c = Decimal(str(close))
        out.append(
            Bar(
                ts=session_bars(current, timeframe)[0],
                open=previous,
                high=max(previous, c),
                low=min(previous, c),
                close=c,
            )
        )
        previous = c
        current += timedelta(days=1)
    return out
