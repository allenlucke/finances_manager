"""Helpers every reader shares.

There used to be two ``account_hash`` implementations, two money parsers that disagreed about what
``(100.00)`` and ``--`` meant, and three ways of handling a byte-order mark — one per reader, each
written on the day that reader met its first real file. They had already drifted. Everything here
is the single version, and a reader that needs a variation should say why in a comment rather than
grow a private copy.
"""

from __future__ import annotations

import hashlib
import hmac
import os
import re
from datetime import date
from decimal import Decimal, InvalidOperation

_WHITESPACE = re.compile(r"\s+")
# Trailing store/reference numbers that make otherwise identical merchants look distinct.
_TRAILING_REF = re.compile(r"[\s#*]+[0-9]{3,}$")

# A money figure after currency symbols and signs are removed: plain digits, or digits grouped in
# threes by the grouping separator, with an optional fraction. Nothing else — no exponent, no
# underscore, no "NaN". Decimal() accepts all three, and none of them is money.
_PLAIN = r"\d+"
_GROUPED_COMMA = r"\d{1,3}(?:,\d{3})+"
_GROUPED_DOT = r"\d{1,3}(?:\.\d{3})+"
_MONEY_DOT_DECIMAL = re.compile(rf"^(?:{_GROUPED_COMMA}|{_PLAIN})?(?:\.\d+)?$")
_MONEY_COMMA_DECIMAL = re.compile(rf"^(?:{_GROUPED_DOT}|{_PLAIN})?(?:,\d+)?$")

# The file saying "not applicable", which is different from zero and different from garbage.
_NOT_APPLICABLE = frozenset({"", "--", "-", "n/a", "N/A", "na", "NA"})

# "84.31 CR" / "CR 84.31" — bank shorthand for credit and debit. Matched as whole tokens only.
_CR_DR = re.compile(r"^(CR|DR)\b\s*|\s*\b(CR|DR)$", re.IGNORECASE)


def decode_text(content: bytes) -> tuple[str, str | None]:
    """Decode an uploaded file, returning the text and a warning when it was not UTF-8.

    UTF-8 with an optional byte-order mark is the policy. The realistic exception is a Windows
    export in cp1252 — a curly apostrophe in a merchant name is enough — and refusing the whole file
    over one byte is the wrong trade, while decoding with ``errors="replace"`` silently corrupts the
    description and therefore the dedupe key. So: strict UTF-8 first, cp1252 second, and say so.
    """
    try:
        return content.decode("utf-8-sig"), None
    except UnicodeDecodeError:
        return (
            content.decode("cp1252", errors="replace"),
            "File was not UTF-8; read as Windows-1252. Check descriptions with accented characters.",
        )


SECRET_VARIABLE = "ACCOUNT_KEY_SECRET"


class AccountKeySecretMissing(RuntimeError):
    """Raised when no per-install secret is configured, rather than falling back to a weaker key."""

    def __init__(self) -> None:
        super().__init__(
            f"{SECRET_VARIABLE} is not set. It keys the one-way account ids the importer links "
            "rows by; `make up` and `make ai` generate one into .env. Refusing to derive a key "
            "without it: an unkeyed hash of an account number is the account number."
        )


def account_key_secret() -> str:
    secret = os.environ.get(SECRET_VARIABLE, "").strip()
    if not secret:
        raise AccountKeySecretMissing()
    return secret


def account_hash(account_number: str) -> str:
    """Stable id for an account number that cannot be turned back into one.

    Lets a row be matched to the same account across re-imports without the full number ever being
    returned or stored (docs/SECURITY.md). It is an HMAC under a per-install secret, and that is
    the whole point: the previous version was a bare truncated SHA-256, and an account number is a
    ten-digit string whose last four sit right beside the key in every response, in the
    ``account`` table, and in whatever the MCP tools hand to the model. Recovering one took
    0.22 seconds. Keyed, the same key is stable across re-imports on the same install and means
    nothing anywhere else.

    32 hex characters (128 bits) — visibly longer than the 16 the old hash produced, which is
    how ``legacy_account_hash`` and this one are told apart during the re-linking release.
    """
    digest = hmac.new(
        account_key_secret().encode("utf-8"),
        account_number.strip().encode("utf-8"),
        hashlib.sha256,
    )
    return digest.hexdigest()[:32]


def legacy_account_hash(account_number: str) -> str:
    """The pre-2026-09-12 key: an unsalted, truncated SHA-256 of the number.

    Emitted beside the real key for one release so the API can find an account that was linked
    under it and re-key the link on the spot. It travels only between the parser and the API and
    is never stored again. Remove this, ``legacy_account_key`` on the wire models, and the
    re-linking branch in ``ImportService.resolveAccount`` once every linked account has been
    imported once under the new key.
    """
    return hashlib.sha256(account_number.strip().encode("utf-8")).hexdigest()[:16]


def parse_money(value: str, *, decimal_separator: str = ".") -> Decimal:
    """Parse a human-formatted money figure. Raises ``ValueError`` for anything that is not one.

    Accepts the ways banks write a figure: ``$1,234.56``, ``+12.34``, ``(100.00)``, ``84.31-``,
    ``84.31 CR``. A bracketed figure, a trailing minus and a DR suffix all mean negative; CR means
    positive.

    Refuses what ``Decimal`` would happily take and no statement ever contains: ``NaN``,
    ``Infinity``, ``1E5``, ``1_000``. And it refuses ``84,31`` when the decimal separator is a dot:
    that is either a European figure of 84.31 or a typo, and reading it as 8,431 — which stripping
    the comma did — is wrong a hundredfold either way. A format whose bank writes decimals with a
    comma sets ``decimal_separator=","``.
    """
    cleaned = value.strip()
    negative = False

    if cleaned.startswith("(") and cleaned.endswith(")"):
        negative = True
        cleaned = cleaned[1:-1].strip()

    found = _CR_DR.search(cleaned)
    if found:
        negative = negative or (found.group(1) or found.group(2)).upper() == "DR"
        cleaned = _CR_DR.sub("", cleaned).strip()

    cleaned = cleaned.replace("$", "").replace(" ", "")
    if cleaned.endswith("-"):
        negative = True
        cleaned = cleaned[:-1]
    if cleaned.startswith("-"):
        negative = True
        cleaned = cleaned[1:]
    elif cleaned.startswith("+"):
        cleaned = cleaned[1:]

    if not cleaned:
        raise ValueError("Empty amount")

    shape = _MONEY_COMMA_DECIMAL if decimal_separator == "," else _MONEY_DOT_DECIMAL
    if not shape.match(cleaned) or not any(ch.isdigit() for ch in cleaned):
        raise ValueError(f"Unparseable amount {value!r}")

    if decimal_separator == ",":
        cleaned = cleaned.replace(".", "").replace(",", ".")
    else:
        cleaned = cleaned.replace(",", "")

    try:
        amount = Decimal(cleaned)
    except InvalidOperation as exc:  # unreachable given the shape check; kept as a backstop
        raise ValueError(f"Unparseable amount {value!r}") from exc
    return -amount if negative else amount


def parse_optional_money(value: str | None, *, decimal_separator: str = ".") -> Decimal | None:
    """``parse_money``, or ``None`` when the file says there is no figure.

    Still raises for garbage. A caller that wants "unreadable means absent" must say so at the call
    site, because for a debit/credit pair that silence turns "unparseable debit" into the misleading
    "row has neither a debit nor a credit".
    """
    if value is None or value.strip() in _NOT_APPLICABLE:
        return None
    return parse_money(value, decimal_separator=decimal_separator)


def normalize_description(description: str) -> str:
    """Collapse a raw statement description toward a stable merchant string.

    Deliberately conservative — it only removes noise that is definitely noise. Aggressive
    normalization loses information the categorizer needs.
    """
    cleaned = _WHITESPACE.sub(" ", description).strip().upper()
    cleaned = _TRAILING_REF.sub("", cleaned)
    return cleaned.strip()


def dedupe_key(
    account_ref: str,
    transaction_date: date,
    amount: Decimal,
    description: str,
    external_id: str | None = None,
) -> str:
    """Stable identity for a transaction, so re-imports don't duplicate.

    **When the export carries the institution's own transaction id, that is the identity.** Nothing
    else can distinguish genuinely separate transactions that happen to match on every visible
    field, and they are not rare: a real month contained three identical transfers to the same payee
    on the same day, distinguishable only by the bank's Transaction Number. Hashing date, amount and
    description alone collapsed them into one and silently lost the other two — money missing from
    a ledger, with nothing to show anything had gone wrong.

    Falling back to the hash is for exports that provide no id (a Chase card CSV). It deliberately
    excludes anything that varies between exports of the same period — row order, posted date,
    formatting — and includes the normalized description so two same-day, same-amount purchases at
    different merchants stay distinct.

    The API computes the same key itself (``TransactionService.dedupeKey``) and treats that as the
    truth; this one exists so the parser's output is honest on its own. Keep the two in step.
    """
    if external_id:
        payload = f"{account_ref}|id|{external_id.strip()}"
    else:
        payload = "|".join(
            [
                account_ref,
                transaction_date.isoformat(),
                f"{amount:.4f}",
                normalize_description(description),
            ]
        )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:32]
