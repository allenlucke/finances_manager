# Cowork brief — UI and QA

A prompt to hand to Claude Cowork. Paste the block below; everything above and after it is for
Allen, not for Cowork. Refreshed 2026-09-05 after review batches 1–4; the previous version predated
holdings, positions import, target cadence, restore, and working passkeys.

**Before you paste:** point it at the throwaway stack, not the one with your data in it.

```bash
make e2e-down          # in case one is already running
make e2e               # runs the 12 browser tests, then leaves web on :4201 and api on :8081 up
```

The scratch stack has its own database and its own volume, and the browser suite leaves it
**empty** — Cowork will see the first-run setup screen and create its own account. Nothing it does
can touch yours. When you're finished, `make e2e-down` disposes of it.

If Cowork can't reach `localhost` from where it runs — and on 2026-09-05 it could not, while its
browser worked fine otherwise — publish the scratch stack on this Mac's LAN address instead and
hand it that URL:

```bash
BIND_ADDR=$(ipconfig getifaddr en0) make e2e     # then use http://<that address>:4201
```

Two consequences of a non-`localhost` address. The stack is visible to anything on the home
network for as long as it runs — fine for an empty throwaway database, so dispose of it after. And
browsers only allow passkeys on a secure context, which plain `http://` on a LAN address is not, so
the Sign-in security page will say the browser does not support passkeys; that is the browser, not
a bug, and the passkey flow stays covered by the automated suite. If even the LAN address is
unreachable, Cowork's browser is off the network entirely: it can still do everything in
**Part 1** — a code and copy review that needs only the repository — and Part 2 is mine to run.

---

## The prompt

> You are doing UI and QA work on a personal finance application. It handles one real person's
> actual money, so a plausible-looking wrong number is the worst possible outcome — worse than a
> crash, because nothing looks broken. Bias your attention accordingly.
>
> **The app.** Angular 22 SPA at `services/web`, talking to a Spring Boot API at `services/api`.
> Screens: dashboard, accounts (with brokerage holdings), transactions (with a deleted list),
> budget, import (statements, brokerage positions, the review queue, import history), sign-in
> security (passkeys), and login. Read `docs/DOMAIN.md` first — the money model is more subtle than
> it looks.
>
> **Six invariants. A violation of any of these is a top-severity finding.**
>
> 1. **Amounts are always positive; a `direction` field carries the sign.** A debit is negative for
>    *every* account type, credit cards included, so net worth is a plain sum. Any screen that
>    negates based on account type is wrong.
> 2. **Transfers are not spending.** Paying a credit card, moving to savings, funding a brokerage —
>    these move money between accounts the user already owns. If any of them appears in a spending
>    total or a budget category, the budget is double-charged, because the purchase was charged when
>    it happened. Transfers are also *correctly* uncategorized; they must not show in the "Needs a
>    category" queue. A **refund** is different: it is negative spending, it does belong in the
>    queue, and it must be bookable against the category it refunds.
> 3. **A negative balance means money is owed.** A credit card showing `-696.31` means $696.31 owed.
>    Colour must never be the only cue for sign — the minus sign has to be present too.
> 4. **Money is never a float.** Look for rounding artifacts: totals ending in `.29999`, a sum of
>    rows disagreeing with a displayed total by a cent, a figure changing when a page is re-sorted.
> 5. **Deleting is soft and reversible**, and deleting one leg of a transfer must remove both. Half a
>    transfer means money left one account and arrived nowhere. Every delete offers Undo, and the
>    Transactions page has a deleted list with Restore.
> 6. **A brokerage account is worth its last holdings snapshot, and the date must travel with the
>    number.** After a positions import the account's balance is market value, not the cash paid in,
>    and the Accounts page and the dashboard's net worth must both say *as of when*. A deposit made
>    after the snapshot is deliberately not in the figure until the next import — that is disclosed
>    by the date, and the disclosure must be present.
>
> **A failed request is never an empty state.** Every screen has three states: loaded, loading, and
> "could not load" with a sentence. If you can make a request fail (stop the API container, or
> throttle the network), no screen may respond with "No accounts yet", `$0.00`, or an empty table.
> The dashboard's net worth shows a dash until it has actually arrived.
>
> **Test empty states first, before entering any data.** A brand-new account with zero transactions
> found a bug where the dashboard rendered the literal text "Invalid Date" as the first thing the
> owner ever saw — a fallback that never fired because the failure value was a truthy string. Every
> screen must be correct with nothing in it. Check each one signed in to a fresh account: dashboard,
> accounts, transactions, budget, import, sign-in security. Look for `Invalid Date`, `NaN`,
> `undefined`, `null`, `$NaN`, `-$0.00`, empty tables with no explanation, and any control that
> appears actionable but does nothing.
>
> **Then the flows**, in this order:
>
> - **First run.** A fresh database offers a setup screen rather than a sign-in. Create an account.
>   Confirm the passphrase field rejects a mismatch and enforces a 12-character minimum, and that the
>   show/hide toggle works on both fields.
> - **Sign-in.** Wrong passphrase says the credentials were not accepted. Five wrong attempts should
>   produce a *distinct* message about too many attempts, not the same credentials message — being
>   told "not accepted" while typing the correct passphrase is what makes people keep trying. Then
>   **sign out and sign straight back in without reloading the page.** That exact sequence was a
>   403 blamed on the passphrase until 2026-09-05.
> - **Passkeys** (Sign-in security, from the account menu). Add one — in Chrome, DevTools → More
>   tools → WebAuthn → "Enable virtual authenticator environment" gives you one without hardware.
>   The page must immediately say the account now requires both passphrase and passkey. Sign out,
>   sign in with the passphrase: you must land on a "Passkey required" step, not the dashboard and
>   not the sign-in form again. Present the passkey; you reach the dashboard. Back on Sign-in
>   security, the passkey is listed with its dates; remove it and the page says the account is back
>   to passphrase only. Each of those steps was broken at some point this month; treat this flow as
>   a regression suite.
> - **Accounts.** Create one of each type. A new account shows a zero balance rather than blank.
>   The page shows the API's net worth, not a sum it did itself.
> - **Transactions.** Record a purchase and confirm it shows negative. Record a transfer between two
>   accounts and confirm *both* balances move, in opposite directions, and that it does not appear in
>   spending. Try to categorize the transfer — it must be refused with an explanation, not silently
>   accepted. Delete a row: an Undo appears; let it expire, then find the row in the deleted list and
>   restore it. Set the date range to something impossible (end before start): the ledger must say
>   so rather than show nothing. Add more rows than one page shows and confirm it says "Showing the
>   newest N of M".
> - **Budget.** Create a category, set a target with a start date and a cadence (weekly, monthly,
>   quarterly, yearly). Set one below what has been spent and confirm the over-budget state is
>   legible without relying on colour. Add a second target overlapping the first: the refusal must
>   name the date. Income categories must be reported separately from spending, in their own words
>   (received / expected), never as "over budget".
> - **Import.** Use only the synthetic fixtures in `services/ai/tests/`. `cacu.csv` is a single
>   checking account with a preamble: choose an account, import it, then import it again — the
>   second time must say nothing new was added, and the import history must show both attempts.
>   `fidelity_history.csv` names accounts that do not exist: the app must list them by the file's
>   own names and offer to create them, and after "create and retry" the rows must land on the new
>   accounts, which are typed brokerage. `positions.csv` goes in through the **Brokerage positions**
>   upload, not the statement one; afterwards the Accounts page shows holdings grouped by account
>   with an as-of date, and re-importing it reports positions *updated*, not duplicated. Then break
>   one: rename a text file to `.csv` and upload it — the error shown must be the parser's own reason
>   ("No parser matches this file…"), not a generic sentence. Finally the review queue: imported
>   purchases appear there, transfers do not, and assigning a category removes the row.
>
> **Accessibility, treated as a real requirement.** Keyboard-only traversal of every flow above.
> Visible focus. Labels tied to inputs. One h1 per page and an h2 per card, in order. Contrast at
> WCAG AA. Colour never carrying meaning alone — this app uses colour for positive and negative
> money and for over-budget, so that one matters here more than usual. Screen-reader labels on
> icon-only buttons (delete, remove passkey, the account menu).
>
> **Responsive.** 320px, 768px, 1440px. Tables of transactions and holdings are the likely failure:
> they must scroll inside their own container and never make the page scroll sideways.
>
> **What I want back**, in priority order:
>
> 1. **Correctness bugs** — anything violating the six invariants, or any wrong number. For each:
>    what you did, what you expected, what happened, and a screenshot.
> 2. **Broken or confusing states** — empty states, error messages that explain nothing, dead ends.
> 3. **Accessibility failures**, with the specific WCAG criterion.
> 4. **UX friction**, ordered by how often it would be hit. This app is used by one person a few
>    times a week; optimise for "obvious three weeks later", not for first-run delight.
>
> For each finding give me severity (critical / major / minor), the exact steps, and a concrete
> suggested fix. Do not fix anything yourself — report it.
>
> **Things that are deliberate, so don't report them:** transfers having no category; **the review
> queue offering no suggested categories** (automatic categorization is the next milestone; today
> the queue is simply "uncategorized and not a transfer"); a brokerage balance not moving when cash
> is deposited after its snapshot (see invariant 6 — the as-of date is the disclosure); a
> re-imported positions file reporting "updated" rather than "duplicate"; removing the last passkey
> returning the account to passphrase only (it is the recovery path); the AI service not being
> reachable from the browser; account numbers showing only the last four; the app looking plain (it
> is a tool, not a product); and there being exactly one user account with no way to register a
> second.
>
> Do not use real financial data, and do not enter any real account number, card number, or
> passphrase you did not create yourself for this test.
```

---

## Notes for Allen

**Part 1 works without a running app.** Everything in "the six invariants" and the copy/empty-state
review can be done against the repository alone — reading templates, checking the wording of error
messages, spotting a fallback that can't fire. That is where the Invalid Date bug lived, and a code
read would have caught it.

**Part 2 needs `localhost:4201` reachable** from wherever Cowork runs. If it can't get there, the
same prompt works against a Playwright run you drive locally, or just ask me to do it.

**Passkeys on the scratch stack work.** `infra/e2e.env` allows the 4201 origin, so a real ceremony
succeeds there; Chrome's virtual authenticator (DevTools → WebAuthn) means no Touch ID prompt.

**Don't give it your passphrase.** The scratch stack starts empty and offers a setup screen, so it
creates its own throwaway account. There is nothing of yours on `:4201`.

**What the automated suite already covers**, so Cowork's time is better spent elsewhere: setup and
sign-in, a purchase, a transfer, a statement upload and re-upload, the passkey ceremony end to end,
and the nginx proxy paths. Empty states, accessibility, responsive layout, holdings, budget cadence,
the deleted list and the unlinked-account flow are where a person's eyes still matter.

**Feed the findings back here.** Anything it reports, paste in and I'll triage — some of it will be
deliberate (the list at the end of the prompt trims the obvious ones, but not all of them), and the
real bugs should land as tests, not just fixes.
