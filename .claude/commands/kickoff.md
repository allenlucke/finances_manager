---
description: Open a working session on this repo — orient, verify the suites, then ask what to build
---

Start by orienting yourself. Every decision in `docs/DECISIONS.md` is settled; do not re-open any.

1. Read `CLAUDE.md` (the working agreements are hard-won and each has a test), then
   `docs/DECISIONS.md`, `docs/DOMAIN.md`, and the status sections of `docs/ROADMAP.md`.
2. Read the newest `docs/REVIEW-*.md`. Its Progress section says what was closed in which batch
   and what is still open; anything listed as open there is the shortlist.
3. Run `make doctor`, then `make test`. If Docker is not up, start it — the Java suite needs
   Testcontainers. A red suite is fixed before anything else.
4. Check `git status`. The dev stack (`finances-manager`, ports 4200/8080) holds Allen's real
   account and is never truncated; the scratch stack (`finances-e2e`, 4201/8081) is throwaway.
   `make doctor` says which are up.
5. Then ask Allen what to work on, or propose the next open item from the review ledger with a
   sentence on why. Do not start feature work before that.

Standing rules: commit per batch with its tests, never push, no real identifiers in tracked files.
