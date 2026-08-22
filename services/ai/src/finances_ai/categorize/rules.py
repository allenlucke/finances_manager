"""Tier 1 of categorization: deterministic merchant rules.

The full design is three tiers, cheapest first (docs/DECISIONS.md D-15):

1. rules            — this module. Free, instant, handles the long tail of repeat merchants.
2. similarity       — nearest neighbour against Allen's own corrected history. Not built yet.
3. model fallback   — an LLM, for genuinely novel merchants only. Not built yet.

The most important job here is not picking the right expense category — it's recognizing what
*isn't an expense at all*. Card payments and inter-account transfers move money that was already
budgeted when the purchase happened; counting them again double-charges the budget. The legacy
schema encoded this with the ``paymentToCreditAccount`` flag and it is the single subtlest piece of
business logic in the project. See docs/DOMAIN.md.
"""

from __future__ import annotations

import re
from collections.abc import Iterable

from finances_ai.models import CategorySuggestion, ParsedTransaction

# Patterns that mean "this moves money between the user's own accounts."
_TRANSFER_PATTERNS: tuple[re.Pattern[str], ...] = tuple(
    re.compile(pattern)
    for pattern in (
        r"\bAUTOPAY\b",
        r"\bPAYMENT\s+THANK\s*YOU\b",
        r"\bONLINE\s+PAYMENT\b",
        r"\bCARDMEMBER\s+SERV\b",
        r"\bE-?PAYMENT\b",
        r"\bTRANSFER\s+(TO|FROM)\b",
        r"\bZELLE\b",
        r"\bINTERNAL\s+TRANSFER\b",
    )
)

# Seed rules. This table is meant to be *grown from Allen's own corrections*, not hand-curated
# forever — every confirmed correction should append or reweight an entry here.
_MERCHANT_RULES: tuple[tuple[str, str], ...] = (
    (r"\b(KROGER|HY-?VEE|ALDI|TRADER JOE|WHOLE FOODS|COSTCO|SAM'?S CLUB)\b", "Groceries"),
    (r"\b(SHELL|QUIKTRIP|QT \d|CASEY'?S|BP#|EXXON|PHILLIPS 66)\b", "Fuel"),
    (r"\b(NETFLIX|SPOTIFY|HULU|DISNEY PLUS|MAX\.COM|YOUTUBEPREMIUM)\b", "Subscriptions"),
    (r"\b(EVERGY|SPIRE|WATER ?ONE|KANSAS CITY WATER|ATMOS)\b", "Utilities"),
    (r"\b(GOOGLE ?\*?CLOUD|AWS|DIGITALOCEAN|GITHUB|ANTHROPIC|OPENAI)\b", "Software & Services"),
    (r"\b(DELTA|SOUTHWEST|UNITED AIR|AMERICAN AIR|MARRIOTT|HILTON)\b", "Travel"),
    (r"\b(WALGREENS|CVS|QUEST DIAG|LABCORP)\b", "Health"),
    (r"\b(HOME DEPOT|LOWE'?S|MENARDS|ACE HARDWARE)\b", "Home & Maintenance"),
    (r"\b(STATE FARM|GEICO|PROGRESSIVE|ALLSTATE)\b", "Insurance"),
    (r"\b(IRS|KDOR|DEPT OF REVENUE|TREASURY)\b", "Taxes"),
)

_COMPILED_RULES: tuple[tuple[re.Pattern[str], str], ...] = tuple(
    (re.compile(pattern), category) for pattern, category in _MERCHANT_RULES
)


def categorize_one(transaction: ParsedTransaction) -> CategorySuggestion:
    """Suggest a category for a single transaction using deterministic rules only."""
    haystack = (transaction.merchant or transaction.description).upper()

    for pattern in _TRANSFER_PATTERNS:
        if pattern.search(haystack):
            return CategorySuggestion(
                dedupe_key=transaction.dedupe_key,
                category=None,
                confidence=0.95,
                method="rule",
                rationale=f"Matched transfer pattern {pattern.pattern!r}",
                is_transfer=True,
            )

    for pattern, category in _COMPILED_RULES:
        if pattern.search(haystack):
            return CategorySuggestion(
                dedupe_key=transaction.dedupe_key,
                category=category,
                confidence=0.9,
                method="rule",
                rationale=f"Matched merchant pattern {pattern.pattern!r}",
            )

    # No rule matched. Tiers 2 and 3 are not built yet; until they are, this is an honest
    # "I don't know" and the API should route it to human review rather than guess.
    return CategorySuggestion(
        dedupe_key=transaction.dedupe_key,
        category=None,
        confidence=0.0,
        method="rule",
        rationale="No rule matched; needs review (similarity and model tiers not yet implemented)",
    )


def categorize(transactions: Iterable[ParsedTransaction]) -> list[CategorySuggestion]:
    return [categorize_one(transaction) for transaction in transactions]
