# Web

The Angular 22 SPA. Everything is driven from the repository root's `Makefile`:

```bash
make web        # dev server on :4200, proxying /api, /webauthn and /login/webauthn to :8080
make test-web   # Vitest unit suite (`npm run test:ci` here)
make e2e        # Playwright, on its own throwaway compose stack — never the dev database
make fmt        # prettier over src/
```

Conventions: standalone components, signals, `inject()`, the new control flow, Angular Material
alone (D-13). Every request sits behind `LoadState`, and every message a person reads has a spec
that reads the DOM. See `CLAUDE.md` at the root for the agreements and why each exists.

Browser tests live in `e2e/`; `playwright.config.ts` defaults to the scratch stack on 4201.
