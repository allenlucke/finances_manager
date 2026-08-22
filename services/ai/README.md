# finances-ai

Stateless parsing and inference service. See `docs/ARCHITECTURE.md` at the repo root.

```bash
uv sync --extra dev
uv run uvicorn finances_ai.app:app --reload --port 8000
uv run pytest
```
