---
description: Open a working session on this repo — review open decisions before writing code
---

Start by orienting yourself, then work through the open questions with Allen.

1. Read `CLAUDE.md`, `docs/DECISIONS.md`, `docs/DOMAIN.md`, and `docs/ROADMAP.md`.
2. Verify the scaffold actually runs here: `make test`. The Java build in particular was authored
   without a compiler available, so treat the first `mvn test` as a real check, not a formality.
   Fix whatever it turns up before anything else.
3. Walk Allen through the **OPEN** items in `docs/DECISIONS.md` one at a time — D-10 through D-16.
   For each: state the tradeoff in two or three sentences, give a recommendation with a reason,
   and wait for his answer. Do not batch them into one wall of text.
4. As each is decided, move it from OPEN to SETTLED in `docs/DECISIONS.md` with the reasoning,
   and update the scaffold to match.
5. Only then confirm M1 scope and start building.
