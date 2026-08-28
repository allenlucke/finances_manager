# services/mcp — talking to your finances from Claude Code

An MCP server that gives Claude Code direct access to the finance API (D-17), so you can say
"what did I spend on groceries last month" or "import the CACU statement from Downloads" and have
it actually happen.

It runs on your machine, against your own API, over loopback. **No Anthropic API key and no API
fees** — the conversation happens in Claude Code, which your Max subscription already covers. That
is the whole point of putting it here rather than building a chat box into the web app.

## Setup

```bash
make mcp-token   # generates LOCAL_API_TOKEN and writes it into .env
make up          # the API only reads the token at startup
```

Then restart Claude Code in this directory. `.mcp.json` at the repo root registers the server, and
it finds the token by reading `.env` itself — nothing to export, nothing to paste.

Check it worked by asking Claude Code for your accounts. If the token is wrong or missing, the
tools say so in a sentence rather than failing obscurely.

## What it can do

Everything the web app can, which was a deliberate choice — see D-17.

**Reading** — `list_accounts`, `net_worth`, `list_entities`, `list_categories`,
`list_transactions`, `review_queue`, `spending`, `reconciliation`, `import_history`,
`list_deleted_transactions`, `find_statement_files`.

**Writing** — `create_entity`, `create_account`, `create_category`, `set_target`,
`record_transaction`, `categorize_transaction`, `import_statement`.

**Removing** — `delete_transaction`, and `restore_transaction` to undo it.

Tools are annotated read-only / destructive, so the client can tell a balance lookup from a delete.

## Why deleting is allowed

Because it is reversible. Deletion in this system is soft: the row stays, its dedupe key stays
claimed, and `restore_transaction` brings it back — both legs, if it was a transfer. `delete_transaction`
returns the exact call that undoes it, so the undo is in hand rather than something to go looking
for. A delete you cannot perform is not safer than a delete you can undo; it is just less useful.

## What it does not do

- **It never sees your passphrase.** It authenticates with a separate token that only works over
  loopback, and which you can revoke by clearing one line in `.env` and restarting the API.
- **It adds no rules of its own.** Every tool is a thin call onto an API endpoint that already
  enforces the domain — money as `BigDecimal`, transfers kept out of spending totals, imports
  deduplicated. There is no second place for those rules to drift to.
- **It does not read statement files unless asked.** `find_statement_files` reports names, sizes
  and dates only, so you can ask what is available without pulling financial data into the chat.

## The security trade, stated plainly

The token satisfies the passkey second factor. It has to, or it could not reach an API that
requires one — but it means registering a passkey no longer covers a caller holding this token.
That is acceptable while the token is loopback-scoped and the deployment is VPN-only (D-16), and
it is why the feature is **off unless a token is set**. Read `docs/SECURITY.md` before enabling it
anywhere that is not this laptop.

One wrinkle worth knowing: in Docker the API cannot enforce loopback itself, because Docker
rewrites the source address of published traffic. The compose file turns the in-process check off
there and relies on the port being published to `127.0.0.1` only — the same boundary the rest of
the app already depends on. Run the API natively (`make api`) and the in-process check applies.

## Development

```bash
make test-mcp    # or: cd services/mcp && uv run pytest -q
make fmt
```

Tests substitute an HTTP transport rather than starting a server, so they are fast and hermetic
while still exercising the token, headers and response handling exactly as in production.
