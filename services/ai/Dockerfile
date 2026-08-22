# syntax=docker/dockerfile:1
FROM python:3.13-slim
WORKDIR /app
COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv
COPY pyproject.toml README.md ./
COPY src ./src
RUN uv pip install --system --no-cache .
EXPOSE 8000
CMD ["uvicorn", "finances_ai.app:app", "--host", "0.0.0.0", "--port", "8000"]
