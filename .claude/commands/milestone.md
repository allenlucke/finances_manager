---
description: Plan and start a roadmap milestone
argument-hint: "[M1|M2|M3|M4|M5|M6]"
---

Plan milestone $ARGUMENTS from `docs/ROADMAP.md`.

- Re-read the milestone's entry and the "Discuss first" decisions it names.
- Check the relevant legacy code under `legacy/` before designing anything — the 2021 app solved
  several of these problems already and the reasoning is often in the SQL comments.
- Produce a concrete task breakdown, flag anything that needs Allen's input, and confirm the plan
  before writing code.
- Every task in the breakdown includes its tests. A task without tests is not finished.
