# Cowork brief — UI and QA

A prompt to hand to Claude Cowork. Paste the block below; everything above and after it is for
Allen, not for Cowork.

**Before you paste:** point it at the throwaway stack, not the one with your data in it.

```bash
make e2e-down          # in case one is already running
make e2e               # brings up web on :4201, api on :8081, and leaves it running
```

The scratch stack has its own database and its own volume. Nothing Cowork does can touch your
account. When you're finished, `make e2e-down` disposes of it.

If Cowork can't reach `localhost` from where it runs, it can still do everything in **Part 1** —
that part is a code and copy review and needs only the repository.

---

## The prompt

> You are doing UI and QA work on a personal finance application. It handles one real person's
> actual money, so a plausible-looking wrong number is the worst possible outcome — worse than a
> crash, because nothing looks broken. Bias your attention accordingly.
>
> **The app.** Angular 22 SPA at `services/web`, talking to a Spring Boot API at `services/api`.
> Screens: dashboard, accounts, transactions, budget, import, security, login. Read
> `docs/DOMAIN.md` first — the money model is more subtle than it looks.
>
> **Five invariants. A violation of any of these is a top-severity finding.**
>
> 1. **Amounts are always positive; a `direction` field carries the sign.** A debit is negative for
>    *every* account type, credit cards included, so net worth is a plain sum. Any screen that
>    negates based on account type is wrong.
> 2. **Transfers are not spending.** Paying a credit card, moving to savings, funding a brokerage —
>    these move money between accounts the user already owns. If any of them appears in a spending
>    total or a budget category, the budget is double-charged, because the purchase was charged when
>    it happened. Transfers are also *correctly* uncategorized; they must not show in a "needs a
>    category" queue.
> 3. **A negative balance means money is owed.** A credit card showing `-696.31` means $696.31 owed.
>    Colour must never be the only cue for sign — the minus sign has to be present too.
> 4. **Money is never a float.** Look for rounding artifacts: totals ending in `.29999`, a sum of
>    rows disagreeing with a displayed total by a cent, a figure changing when a page is re-sorted.
> 5. **Deleting is soft and reversible**, and deleting one leg of a transfer must remove both. Half a
>    transfer means money left one account and arrived nowhere.
>
> **Test empty states first, before entering any data.** A brand-new account with zero transactions
> found a bug where the dashboard rendered the literal text "Invalid Date" as the first thing the
> owner ever saw — a fallback that never fired because the failure value was a truthy string. Every
> screen must be correct with nothing in it. Check each one signed in to a fresh account: dashboard,
> accounts, transactions, budget, import, security. Look for `Invalid Date`, `NaN`, `undefined`,
> `null`, `$NaN`, `-$0.00`, empty tables with no explanation, and any control that appears actionable
> but does nothing.
>
> **Then the flows**, in this order:
>
> - **First run.** A fresh database offers a setup screen rather than a sign-in. Create an account.
>   Confirm the passphrase field rejects a mismatch and enforces a 12-character minimum, and that the
>   show/hide toggle works on both fields.
> - **Sign-in.** Wrong passphrase says the credentials were not accepted. Five wrong attempts should
>   produce a *distinct* message about being locked out, not the same credentials message — being
>   told "not accepted" while typing the correct passphrase is what makes people keep trying.
> - **Accounts.** Create one of each type. A new account shows a zero balance rather than blank.
> - **Transactions.** Record a purchase and confirm it shows negative. Record a transfer between two
>   accounts and confirm *both* balances move, in opposite directions, and that it does not appear in
>   spending. Try to categorize the transfer — it must be refused with an explanation, not silently
>   accepted.
> - **Budget.** Set a target below what has been spent and confirm the over-budget state is legible
>   without relying on colour.
> - **Import.** Use a fixture from `services/ai/tests/` (they are synthetic). Import it twice — the
>   second time must report duplicates and add nothing. Import `fidelity_history.csv`, which names
>   accounts that do not exist; it must list them and refuse to guess, not file them somewhere.
>
> **Accessibility, treated as a real requirement.** Keyboard-only traversal of every flow above.
> Visible focus. Labels tied to inputs. Sensible heading order. Contrast at WCAG AA. Colour never
> carrying meaning alone — this app uses colour for positive and negative money, so that one matters
> here more than usual. Screen-reader labels on icon-only buttons.
>
> **Responsive.** 320px, 768px, 1440px. Tables of transactions are the likely failure: they must
> scroll inside their own container and never make the page scroll sideways.
>
> **What I want back**, in priority order:
>
> 1. **Correctness bugs** — anything violating the five invariants, or any wrong number. For each:
>    what you did, what you expected, what happened, and a screenshot.
> 2. **Broken or confusing states** — empty states, error messages that explain nothing, dead ends.
> 3. **Accessibility failures**, with the specific WCAG criterion.
> 4. **UX friction**, ordered by how often it would be hit. This app is used by one person a few
>    times a week; optimise for "obvious three weeks later", not for first-run delight.
>
> For each finding give me severity (critical / major / minor), the exact steps, and a concrete
> suggested fix. Do not fix anything yourself — report it.
>
> **Things that are deliberate, so don't report them:** transfers having no category; the AI service
> not being reachable from the browser; account numbers showing only the last four; the app looking
> plain (it is a tool, not a product); and there being exactly one user account with no way to
> register a second.
>
> Do not use real financial data, and do not enter any real account number, card number, or
> passphrase you did not create yourself for this test.
```

---

## Notes for Allen

**Part 1 works without a running app.** Everything in "the five invariants" and the copy/empty-state
review can be done against the repository alone — reading templates, checking the wording of error
messages, spotting a fallback that can't fire. That is where the Invalid Date bug lived, and a code
read would have caught it.

**Part 2 needs `localhost:4201` reachable** from wherever Cowork runs. If it can't get there, the
same prompt works against a Playwright run you drive locally, or just ask me to do it.

**Don't give it your passphrase.** The scratch stack starts empty and offers a setup screen, so it
creates its own throwaway account. There is nothing of yours on `:4201`.

**Feed the findings back here.** Anything it reports, paste in and I'll triage — some of it will be
deliberate (the list at the end of the prompt trims the obvious ones, but not all of them), and the
real bugs should land as tests, not just fixes.
