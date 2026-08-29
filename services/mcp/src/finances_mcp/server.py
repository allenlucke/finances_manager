"""MCP server for the finances API (D-17).

Lets Claude Code drive the finance app directly: read balances and spending, record and categorize
transactions, import statements, and undo mistakes. The API remains the system of record — every
tool here is a thin call onto an endpoint that already enforces the rules.

Two conventions from docs/DOMAIN.md are repeated in the tool descriptions rather than left implicit,
because getting either wrong produces a wrong number that still looks plausible:

* **Amounts are always positive**; ``direction`` carries the sign. A debit reduces an account's
  balance for every account type, so net worth is a plain sum.
* **Transfers are not spending.** Paying a credit card moves money between two accounts the user
  already owns; counting it as an expense double-charges the budget, because the purchase was
  charged when it happened.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any

from mcp.server.mcpserver import MCPServer
from mcp.types import ToolAnnotations

from . import env
from .client import ApiError, FinancesClient

mcp = MCPServer("finances")

# Hints the client can use to decide what needs confirming. Marking the reads read-only is what
# earns the writes their weight: if everything looked equally consequential, nothing would.
READS = ToolAnnotations(read_only_hint=True)
WRITES = ToolAnnotations(read_only_hint=False, destructive_hint=False)
REMOVES = ToolAnnotations(read_only_hint=False, destructive_hint=True)

_client: FinancesClient | None = None


def client() -> FinancesClient:
    """The API client, created on first use so import never fails on a missing token."""
    global _client
    if _client is None:
        _client = FinancesClient()
    return _client


def _page(result: Any, page: int, size: int) -> Any:
    """Flattens Spring's ``Page`` envelope into something predictable.

    Some endpoints return a page object and some a bare list, which is a fine distinction inside
    the API and a trap out here: a caller that does the obvious thing and iterates gets the *field
    names* of the envelope instead of any transactions, and nothing raises. Every listing tool
    therefore answers the same shape, with ``has_more`` stated rather than left to be inferred from
    arithmetic on totals.
    """
    if isinstance(result, dict) and "error" in result:
        return result
    if isinstance(result, dict) and "content" in result:
        rows = result["content"]
        total = result.get("totalElements", len(rows))
    else:
        rows = result or []
        total = len(rows)
    return {
        "transactions": rows,
        "total": total,
        "page": page,
        "page_size": size,
        "has_more": (page + 1) * size < total,
    }


def _guard(call: Any) -> Any:
    """Turns an API refusal into a sentence rather than a stack trace.

    An MCP tool that raises gives the model an opaque failure; one that returns the reason lets it
    explain the problem or fix the call. Server faults are re-raised — those are not the model's to
    work around, and quietly reporting one as a result would hide a real outage.
    """
    try:
        return call()
    except ApiError as error:
        if error.status >= 500:
            raise
        return {"error": error.detail, "status": error.status}


# ---------------------------------------------------------------------------------------------
# Reading
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=READS)
def list_accounts() -> Any:
    """Every account with its current balance.

    Balances are signed: negative means money is owed (a credit card balance of -696.31 means
    $696.31 is owed on it), positive means money is held.
    """
    return _guard(lambda: client().get("/api/v1/accounts"))


@mcp.tool(annotations=READS)
def net_worth() -> Any:
    """Net worth overall and per set of books.

    The row with a null ``ledgerEntityId`` is the combined total; the others are per entity
    (personal vs Feeling Froggy). Use ``list_entities`` to turn those ids into names.
    """
    return _guard(lambda: client().get("/api/v1/reports/net-worth"))


@mcp.tool(annotations=READS)
def list_entities() -> Any:
    """The sets of books — personal and business. Every account belongs to exactly one."""
    return _guard(lambda: client().get("/api/v1/entities"))


@mcp.tool(annotations=READS)
def list_categories() -> Any:
    """Spending and income categories, with their kind (``expense`` or ``income``)."""
    return _guard(lambda: client().get("/api/v1/categories"))


@mcp.tool(annotations=READS)
def list_transactions(
    date_from: str | None = None,
    date_to: str | None = None,
    page: int = 0,
    size: int = 50,
) -> Any:
    """Transactions in a date range, newest first.

    Args:
        date_from: ISO date (YYYY-MM-DD) to start from. Omit for no lower bound.
        date_to: ISO date to stop at. Omit for no upper bound.
        page: zero-based page number.
        size: rows per page, up to 500.

    Returns ``transactions`` along with ``total`` and ``has_more`` for paging.

    ``signedAmount`` is the one to sum: negative for money out, positive for money in, for every
    account type. Deleted rows are never included — use ``list_deleted_transactions`` for those.
    """
    return _page(
        _guard(
            lambda: client().get(
                "/api/v1/transactions",
                **{"from": date_from, "to": date_to, "page": page, "size": size},
            )
        ),
        page,
        size,
    )


@mcp.tool(annotations=READS)
def review_queue(page: int = 0, size: int = 50) -> Any:
    """Transactions that still need a category.

    Transfers are deliberately excluded: an uncategorized transfer is correct, not pending, and
    giving one a spending category would double-count the money.

    Returns ``transactions`` along with ``total`` and ``has_more`` for paging.
    """
    return _page(
        _guard(lambda: client().get("/api/v1/transactions/review", page=page, size=size)),
        page,
        size,
    )


@mcp.tool(annotations=READS)
def spending(date_from: str | None = None, date_to: str | None = None) -> Any:
    """Spending by category against its budget target, per month.

    Args:
        date_from: ISO date; defaults to the start of the current month on the server.
        date_to: ISO date; defaults to today.

    Returns one row per category per month, with ``netAmount`` spent, the ``targetAmount`` if a
    budget is set, and what ``remaining``.
    """
    return _guard(
        lambda: client().get(
            "/api/v1/reports/spend-vs-target", **{"from": date_from, "to": date_to}
        )
    )


@mcp.tool(annotations=READS)
def reconciliation() -> Any:
    """Statement closing balances against what the ledger computes.

    A non-zero ``difference`` means a transaction is missing, duplicated, or has the wrong amount —
    it is the strongest signal that an import went wrong.
    """
    return _guard(lambda: client().get("/api/v1/reports/reconciliation"))


@mcp.tool(annotations=READS)
def import_history() -> Any:
    """Past statement imports: file, status, rows applied, and any warning recorded."""
    return _guard(lambda: client().get("/api/v1/imports"))


@mcp.tool(annotations=READS)
def list_holdings(account_id: int | None = None) -> Any:
    """What is owned — the most recent positions snapshot, largest position first.

    Args:
        account_id: limit to one account. Omit for everything.

    Each row carries ``asOf``, the date of the snapshot it came from. A market value is only as
    current as the last positions file imported, so check it before quoting a figure as today's.

    ``marketValue`` is always positive here — a holding is a magnitude, not a signed ledger amount.
    Cash and money-market rows (``cash: true``) are included, because a brokerage's uninvested cash
    is part of what the account is worth.
    """
    path = "/api/v1/holdings" if account_id is None else f"/api/v1/holdings/account/{account_id}"
    return _guard(lambda: client().get(path))


@mcp.tool(annotations=READS)
def list_deleted_transactions(page: int = 0, size: int = 100) -> Any:
    """Transactions that have been deleted, most recently deleted first.

    Deletion here is soft, so anything listed can be brought back with ``restore_transaction``.

    Returns ``transactions`` along with ``total`` and ``has_more`` for paging.
    """
    return _page(
        _guard(lambda: client().get("/api/v1/transactions/deleted", page=page, size=size)),
        page,
        size,
    )


# ---------------------------------------------------------------------------------------------
# Writing
# ---------------------------------------------------------------------------------------------


@mcp.tool(annotations=WRITES)
def create_entity(name: str, kind: str) -> Any:
    """Create a set of books.

    Args:
        name: what to call it, e.g. "Feeling Froggy LLC".
        kind: ``personal`` or ``business``.
    """
    return _guard(lambda: client().post("/api/v1/entities", {"name": name, "kind": kind}))


@mcp.tool(annotations=WRITES)
def create_account(
    name: str,
    account_type: str,
    ledger_entity_id: int,
    mask: str | None = None,
    currency: str | None = None,
) -> Any:
    """Create an account.

    Args:
        name: the account's name, e.g. "CACU Checking".
        account_type: one of ``checking``, ``savings``, ``credit_card``, ``brokerage``, ``loan``,
            ``cash``.
        ledger_entity_id: which set of books it belongs to — see ``list_entities``.
        mask: last four digits, for recognising it. Never the full account number.
        currency: ISO code, defaults to USD.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/accounts",
            {
                "name": name,
                "accountType": account_type,
                "ledgerEntityId": ledger_entity_id,
                "mask": mask,
                "currency": currency,
            },
        )
    )


@mcp.tool(annotations=WRITES)
def create_category(name: str, kind: str, parent_id: int | None = None) -> Any:
    """Create a category.

    Args:
        name: e.g. "Groceries".
        kind: ``expense`` or ``income``.
        parent_id: optional parent, for a sub-category.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/categories", {"name": name, "kind": kind, "parentId": parent_id}
        )
    )


@mcp.tool(annotations=WRITES)
def set_target(
    category_id: int,
    ledger_entity_id: int,
    amount: str,
    cadence: str | None = None,
    effective_from: str | None = None,
    note: str | None = None,
) -> Any:
    """Set a budget target for a category.

    Args:
        category_id: the category to budget.
        ledger_entity_id: which set of books.
        amount: a decimal string such as "450.00". Pass a string, never a float — binary floats
            cannot represent most money amounts exactly.
        cadence: ``weekly``, ``monthly``, ``quarterly`` or ``yearly``. Defaults to monthly.
        effective_from: ISO date this target starts applying.
        note: free text.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/targets",
            {
                "categoryId": category_id,
                "ledgerEntityId": ledger_entity_id,
                "amount": amount,
                "cadence": cadence,
                "effectiveFrom": effective_from,
                "note": note,
            },
        )
    )


@mcp.tool(annotations=WRITES)
def record_transaction(
    account_id: int,
    transaction_date: str,
    amount: str,
    direction: str,
    description: str,
    category_id: int | None = None,
    transfer: bool = False,
    transfer_account_id: int | None = None,
) -> Any:
    """Record a transaction by hand.

    Args:
        account_id: the account it happened on.
        transaction_date: ISO date (YYYY-MM-DD).
        amount: a positive decimal string, e.g. "84.31". Always positive — the sign comes from
            ``direction``. Pass a string, never a float.
        direction: ``debit`` for money leaving the account, ``credit`` for money arriving. A debit
            is negative for every account type, including credit cards.
        description: what it was.
        category_id: optional. Leave unset to put it in the review queue.
        transfer: set true when moving money between two accounts the user already owns — paying a
            credit card, moving to savings, funding the brokerage. This writes *both* legs, so the
            other account is updated too, and keeps the amount out of spending totals. Categorizing
            it instead would double-count money that was already charged when it was spent.
        transfer_account_id: the other account. Required when ``transfer`` is true; setting it
            without ``transfer`` records an ordinary one-sided transaction.
    """
    return _guard(
        lambda: client().post(
            "/api/v1/transactions",
            {
                "accountId": account_id,
                "transactionDate": transaction_date,
                "amount": amount,
                "direction": direction,
                "description": description,
                "categoryId": category_id,
                "transfer": transfer or None,
                "transferAccountId": transfer_account_id,
            },
        )
    )


@mcp.tool(annotations=WRITES)
def categorize_transaction(transaction_id: int, category_id: int | None) -> Any:
    """Set or clear a transaction's category.

    Pass ``category_id`` as null to clear it. Transfers cannot be categorized and will be refused —
    that is the double-counting guard doing its job, not an error to work around.
    """
    return _guard(
        lambda: client().put(
            f"/api/v1/transactions/{transaction_id}/category", {"categoryId": category_id}
        )
    )


@mcp.tool(annotations=REMOVES)
def delete_transaction(transaction_id: int) -> Any:
    """Delete a transaction.

    The deletion is soft and fully reversible with ``restore_transaction`` — nothing is destroyed.
    Deleting one leg of a transfer removes both, since half a transfer would show money leaving one
    account without arriving anywhere.
    """
    result = _guard(lambda: client().delete(f"/api/v1/transactions/{transaction_id}"))
    if isinstance(result, dict) and "error" in result:
        return result
    return {
        "deleted": transaction_id,
        "reversible": True,
        "undo_with": f"restore_transaction({transaction_id})",
    }


@mcp.tool(annotations=WRITES)
def restore_transaction(transaction_id: int) -> Any:
    """Undo a delete, bringing the transaction back exactly as it was.

    Restores every leg removed by that same delete, so an undone transfer is whole again. Returns
    404 if the id is not a deleted transaction — including when it has already been restored.
    """
    return _guard(lambda: client().post(f"/api/v1/transactions/{transaction_id}/restore"))


@mcp.tool(annotations=WRITES)
def import_statement(file_path: str, account_id: int | None = None) -> Any:
    """Import a statement file (CSV, OFX or QFX) from this machine.

    Args:
        file_path: path to the file, e.g. "~/Downloads/cacu.csv". Expanded, so ``~`` works.
        account_id: which account the rows belong to. Optional for exports that name an account on
            every row, such as a Fidelity history covering the whole portfolio; required for a
            single-account statement, which carries nothing to identify itself by.

    Safe to repeat: rows already present are counted as duplicates and skipped, so importing the
    same file twice is a no-op rather than a doubling. If the file names accounts that do not exist
    here, the result lists them under ``unlinkedAccounts`` and nothing is guessed — create them and
    import again.
    """
    path = Path(file_path).expanduser()
    if not path.is_file():
        return {"error": f"No file at {path}", "status": 400}
    return _guard(
        lambda: client().upload("/api/v1/imports", path.name, path.read_bytes(), account_id)
    )


@mcp.tool(annotations=WRITES)
def import_positions(file_path: str) -> Any:
    """Import a brokerage positions export (a holdings snapshot) from this machine.

    Args:
        file_path: path to the file, e.g. "~/Downloads/Portfolio_Positions_Aug-27-2026.csv".

    A positions file is a *snapshot* of what is held, not a transaction history — it records no
    money movement and adds nothing to the ledger. Use ``import_statement`` for a transactions
    export such as Fidelity's Accounts History; the two files are different and are not
    interchangeable.

    Importing one changes how the account's balance is computed: from then on the account is worth
    its holdings' market value rather than the sum of cash paid in, which is the difference between
    a correct net worth and one understated by every dollar of growth.

    Safe to repeat. Re-importing the same file updates each position in place; a file downloaded on
    a later date lands as a new snapshot beside the old one, which is what gives a position any
    history at all.
    """
    path = Path(file_path).expanduser()
    if not path.is_file():
        return {"error": f"No file at {path}", "status": 400}
    return _guard(
        lambda: client().upload("/api/v1/imports/positions", path.name, path.read_bytes(), None)
    )


@mcp.tool(annotations=READS)
def find_statement_files(directory: str = "~/Downloads") -> Any:
    """List importable statement files in a directory, newest first.

    Args:
        directory: where to look. Defaults to the Downloads folder.

    Reads nothing — this only reports names, sizes and modification times, so it is a safe way to
    find out what is available before choosing what to import.
    """
    folder = Path(directory).expanduser()
    if not folder.is_dir():
        return {"error": f"No directory at {folder}", "status": 400}

    found = [
        {
            "path": str(entry),
            "name": entry.name,
            "bytes": entry.stat().st_size,
            "modified": int(entry.stat().st_mtime),
        }
        for entry in folder.iterdir()
        if entry.is_file() and entry.suffix.lower() in {".csv", ".ofx", ".qfx"}
    ]
    return sorted(found, key=lambda item: item["modified"], reverse=True)


def main() -> None:
    """Entry point for `finances-mcp`, speaking MCP over stdio."""
    # The repo's .env is the single home for the token; Claude Code launches this with the editor's
    # environment, which knows nothing about it.
    env.load(Path(__file__).resolve().parent)

    # Checked at startup so a missing token fails here, where the message is visible, rather than
    # on the first tool call where it surfaces as an unexplained error mid-conversation.
    if not os.environ.get("LOCAL_API_TOKEN"):
        raise SystemExit(
            "LOCAL_API_TOKEN is not set, and no .env above this file defines it.\n"
            "Generate one with `make mcp-token`, which writes it to .env and tells you what to "
            "restart."
        )
    mcp.run()


if __name__ == "__main__":
    main()
