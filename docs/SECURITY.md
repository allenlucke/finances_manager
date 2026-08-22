# Security notes

This system will eventually hold the complete financial picture of a person and a business. That
raises the stakes above a normal side project. None of the below is exotic — it's just non-optional.

## Secrets

- No credential, token, or key is ever committed. `.env` is gitignored; `.env.example` documents
  every variable by name with a dummy value.
- Aggregator access tokens and any bank credentials are **encrypted at rest** in a dedicated table,
  never in application logs, never in a JSON response, never in an error message.
- Decide on a key management approach before the first real token is stored. Environment-variable
  keys are acceptable for a single-host deployment; hardcoded keys are not, at any stage, including
  "just for testing."

## Data handling

- Account numbers are stored masked (last four) unless there's a concrete reason for the full value.
- Statement files uploaded for parsing are retained only as long as the import needs them, then
  deleted or moved to encrypted storage. They are the single richest thing an attacker could take.
- Logs never contain transaction descriptions, balances, or account identifiers at INFO level.

## The AI service specifically

- It gets transaction data, not credentials. It has no database access and no outbound network
  access except to whatever inference endpoint is configured.
- If a hosted LLM is used for categorization or PDF extraction, understand exactly what leaves the
  machine. Sending a full statement to a third-party API is a real decision with a real answer —
  it is not automatically wrong, but it should be deliberate and documented here when made.
  Local inference via Ollama is the alternative and is a legitimate reason to keep a GPU around.

## Auth

- Legacy used `jjwt 0.9.1` with a symmetric secret. That library version has known vulnerabilities
  and none of that code is carried forward.
- Passwords: modern hashing (argon2id or bcrypt via Spring Security defaults). No exceptions.
- MFA before this app ever faces the public internet with real data in it. See D-12.

## Backups

Before real data goes in, there is a tested restore path. An untested backup is not a backup, and
a personal ledger with three years of categorization corrections in it is genuinely irreplaceable.
