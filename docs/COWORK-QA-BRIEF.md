# Cowork brief — UI and QA

Two things live here: **a short prompt to paste**, and **the reference it points at**. Cowork has
the repository, so the detail does not belong in the prompt — it reads the reference itself.

Refreshed 2026-09-05 after review batches 1–4.

---

## The prompt

Paste this. Replace the URL if the app is running somewhere else, or delete that line if it is not
running at all — the job still works without it.

> You are doing a QA pass on a personal finance app. The repository is here; the app is running at
> **http://192.168.87.21:4201** with an empty database.
>
> **The job in one sentence:** find places where this app shows a wrong number, a confusing state,
> or a control someone cannot use — and report them. Do not fix anything.
>
> One person's real money runs through this app, so a wrong number that *looks plausible* is the
> worst possible outcome. Worse than a crash: nothing looks broken. Weight your attention that way.
>
> **Start by reading two files in the repo:**
>
> - `docs/COWORK-QA-BRIEF.md`, the "Reference" section — seven rules this app must never break, what
>   to click if you can reach it, and a list of things that look wrong but are deliberate.
> - `docs/DOMAIN.md` — the money model, which is subtler than it looks.
>
> **Then do these, in order. Stop and report whenever you have enough; partial is fine.**
>
> 1. **Read the UI code against the seven rules.** It is Angular, in `services/web/src/app`.
>    Templates are `.html`, logic is `.ts`. This needs no running app, and it is where the worst bug
>    so far was found — a fallback that could never fire, so a new user's dashboard read
>    "Invalid Date".
> 2. **Read every message the app can show a person** — errors, empty states, confirmations. Flag
>    any that explains nothing, blames the wrong thing, or would not make sense three weeks later.
> 3. **If you can open the URL above**, work through the checks in the reference. Screenshot
>    anything wrong. If you cannot open it, say so and skip this.
>
> **Report back, worst first.** For each finding give me: severity (critical / major / minor), where
> it is (`file.ts:42`, or the steps to see it), what you expected, what actually happens, and a
> suggested fix in a sentence. A short list of real problems beats a long list of maybes.

---

## The continuation prompt

For a second run, after findings have come back and been fixed. Same shape: short, and it reads the
reference itself.

> You are continuing a QA pass on a personal finance app. The repository is here; the app is running
> at **http://192.168.87.21:4201**. It was rebuilt since your last run, so the database is empty
> again and the account you made is gone — you will land on the setup screen.
>
> **The three findings you sent are fixed and verified. Do not report them again:** the passphrase
> mismatch now says so in text as well as colour, the hint no longer prints through the Confirm box,
> and all three states of the signed-out screen have real headings.
>
> **Do these in order, and send what you have whenever you have it. Partial is fine.**
>
> 1. **Send the outstanding list from your earlier code review.** You mentioned one and it never
>    reached me. It needs no browser, so no approval limit affects it.
> 2. **Empty states on the five screens you never got to** — dashboard, accounts, transactions,
>    budget, import — signed in with no data entered. Look for `Invalid Date`, `NaN`, `undefined`,
>    `null`, `$NaN`, `-$0.00`, empty tables with no explanation, and controls that look actionable
>    but do nothing.
> 3. **The flows**, from "What to check with the app running" in `docs/COWORK-QA-BRIEF.md`. Skip
>    sign-in and the statement upload: automated browser tests already cover both. Holdings, budget
>    cadence, the deleted list and the unlinked-account import are where no test reaches.
> 4. **Accessibility and the three widths.** Keyboard only, visible focus, heading order, contrast,
>    and colour never carrying meaning alone.
>
> If every action still needs its own approval, do steps 1 and 2 and stop. Those are worth more than
> a partial crawl through step 3.
>
> Report as before: severity, where it is, what you expected, what happened, and a one-sentence
> suggested fix. Do not fix anything.

---

## Reference

*This section is for Cowork to read from the repo. It is not meant to be pasted.*

### The seven rules

A violation of any of these is a top-severity finding.

1. **Amounts are always positive; a separate `direction` field carries the sign.** A debit is
   negative for *every* account type, credit cards included, so net worth is a plain sum. Any screen
   that flips a sign based on account type is wrong.
2. **Transfers are not spending.** Paying a credit card, moving money to savings, funding a
   brokerage — these move money between accounts the person already owns. If one appears in a
   spending total or a budget category, the budget is charged twice, because the purchase was
   already charged when it happened. Transfers are also *correctly* uncategorized and must not
   appear in the "Needs a category" queue. A **refund** is the opposite case: it is negative
   spending, it belongs in that queue, and it must be bookable against the category it refunds.
3. **A negative balance means money is owed.** A credit card showing `-696.31` means $696.31 owed.
   Colour must never be the only cue for sign — the minus sign has to be there too.
4. **Money is never a float.** Look for rounding artifacts: a total ending `.29999`, a column of
   rows disagreeing with its own total by a cent, a figure that changes when the page is re-sorted.
5. **Deleting is soft and reversible**, and deleting one leg of a transfer must remove both. Half a
   transfer means money left one account and arrived nowhere. Every delete offers Undo, and the
   Transactions page has a deleted list with Restore.
6. **A brokerage balance is its last holdings snapshot, and the date must travel with the number.**
   After a positions import the account is worth market value, not the cash paid into it, and both
   the Accounts page and the dashboard's net worth must say *as of when*.
7. **A failed request is never an empty state.** Every screen has three states: loaded, loading, and
   "could not load" with a sentence. A request that fails must never produce "No accounts yet",
   `$0.00`, or a bare empty table. The dashboard's net worth shows a dash until it truly arrives.

### What to check with the app running

**Empty states first, before entering any data.** Sign in and visit every screen with nothing in it:
dashboard, accounts, transactions, budget, import, sign-in security. Look for `Invalid Date`, `NaN`,
`undefined`, `null`, `$NaN`, `-$0.00`, an empty table with no explanation, and any control that
looks actionable but does nothing.

**Then the flows:**

- **First run.** An empty database offers a setup screen, not a sign-in. Create an account. The
  passphrase field should reject a mismatch and enforce 12 characters, and show/hide should work on
  both fields.
- **Sign-in.** A wrong passphrase says the credentials were not accepted. Five wrong tries should
  give a *different* message about too many attempts — being told "not accepted" while typing the
  correct passphrase is what makes people keep trying. Then **sign out and straight back in without
  reloading**; that exact sequence was broken until 2026-09-05.
- **Passkeys** (Sign-in security, in the account menu). Only works on `localhost` or HTTPS. If the
  page says the browser does not support passkeys, you are on a plain-HTTP address — that is the
  browser, not a bug. Where it does work: add one, and the page should immediately say both
  passphrase and passkey are required. Sign out, sign in with the passphrase, and you should land on
  a "Passkey required" step — not the dashboard, and not the sign-in form again. Present it, reach
  the dashboard, then remove it and the page should say the account is back to passphrase only.
- **Accounts.** Create one of each type. A new account shows a zero balance, not a blank.
- **Transactions.** Record a purchase; it shows negative. Record a transfer between two accounts;
  *both* balances move in opposite directions and it stays out of spending. Try to categorize the
  transfer — it must be refused with an explanation, not silently accepted. Delete a row, watch for
  Undo, let it expire, then restore the row from the deleted list. Set the date range backwards (end
  before start): the ledger should say so rather than show nothing.
- **Budget.** Create a category and set a target with a start date and a cadence. Set one below what
  has been spent and check the over-budget state is legible without colour. Add a second target
  overlapping the first: the refusal should name the date. Income is reported in its own words
  (received / expected), never as "over budget".
- **Import.** Use only the synthetic fixtures in `services/ai/tests/`. `cacu.csv` is one checking
  account: choose an account, import it, import it again — the second time must add nothing, and the
  history shows both attempts. `fidelity_history.csv` names accounts that do not exist here: the app
  must list them and offer to create them, and after create-and-retry the rows land on those new
  accounts. `positions.csv` goes through the **Brokerage positions** upload, not the statement one;
  afterwards Accounts shows holdings with an as-of date, and re-importing reports positions
  *updated*. Then rename a text file to `.csv` and upload it — the error must be the parser's own
  words ("No parser matches this file…"), not a generic sentence.

**Accessibility, as a real requirement.** Keyboard-only through every flow above. Visible focus.
Labels tied to inputs. One h1 per page, h2 per card, in order. Contrast at WCAG AA. Colour never
carrying meaning alone — this app uses colour for positive and negative money and for over-budget,
so it matters here more than usual. Screen-reader labels on icon-only buttons.

**Responsive** at 320px, 768px and 1440px. Transaction and holdings tables are the likely failure:
they must scroll inside their own container and never make the page scroll sideways.

### Deliberate — do not report these

- Transfers having no category.
- **The review queue offering no suggested categories.** Automatic categorization is the next
  milestone; today the queue is simply "uncategorized and not a transfer".
- A brokerage balance not moving when cash is deposited after its snapshot (rule 6 — the as-of date
  is the disclosure).
- A re-imported positions file reporting "updated" rather than "duplicate".
- Removing the last passkey returning the account to passphrase only — that is the recovery path.
- Passkeys being unavailable on a plain-HTTP LAN address.
- The AI service not being reachable from the browser.
- Account numbers showing only the last four.
- The app looking plain. It is a tool, not a product.
- Exactly one user account, with no way to register a second.

### Ground rules

No real financial data. Do not enter any real account number, card number, or passphrase.

---

## Notes for Allen

**Running the stack for it:**

```bash
make e2e-down          # in case one is already running
make e2e               # runs the 12 browser tests, then leaves web on :4201, api on :8081
```

That stack has its own database and volume, and the suite leaves it empty, so Cowork sees the
first-run setup screen and creates its own account. Nothing it does can touch yours. `make e2e-down`
disposes of it.

**If Cowork cannot reach `localhost`** — on 2026-09-05 it could not, while its own browser worked
fine — publish on this Mac's LAN address instead and give Cowork that URL:

```bash
BIND_ADDR=$(ipconfig getifaddr en0) make e2e     # then http://<that address>:4201
```

Two consequences. The stack is visible to anything on the home network while it runs, which is fine
for an empty throwaway database — dispose of it after. And passkeys will not work there, because
plain HTTP on a LAN address is not a secure context; the automated suite covers that flow instead.
If even the LAN address is refused, Cowork's browser is off your network entirely, and the code
review is the useful half.

**Don't give it your passphrase.** The scratch stack starts empty and creates its own account.

**What the automated suite already covers**, so Cowork's time is better spent elsewhere: setup and
sign-in, a purchase, a transfer, a statement upload and re-upload, the full passkey ceremony, and
the nginx proxy paths. Empty states, accessibility, responsive layout, holdings, budget cadence, the
deleted list and the unlinked-account flow are where a person's eyes still matter.

**Feed the findings back here.** Paste whatever it reports and I'll triage — some will be
deliberate, and the real bugs should land as tests, not just fixes.
