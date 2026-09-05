# syntax=docker/dockerfile:1
FROM python:3.13-slim
WORKDIR /app

# Pinned. This binary installs every dependency of the service that parses bank statements; it
# used to be pulled as `:latest`, an unpinned executable at the root of the supply chain.
COPY --from=ghcr.io/astral-sh/uv:0.12.5 /uv /usr/local/bin/uv

# Resolve from the lockfile, not from version ranges. The image used to copy pyproject.toml alone
# and resolve fresh at build time, so CI tested one set of versions and the container shipped
# another. --frozen refuses to proceed if the lock is stale, which is the point.
COPY pyproject.toml uv.lock README.md ./
RUN uv export --frozen --no-dev --no-emit-project --no-hashes -o /tmp/requirements.txt \
    && uv pip install --system --no-cache -r /tmp/requirements.txt \
    && rm /tmp/requirements.txt
COPY src ./src
RUN uv pip install --system --no-cache --no-deps .

# Not root. This process parses untrusted files from banks; compose additionally runs it with a
# read-only filesystem and no capabilities, and PYTHONDONTWRITEBYTECODE keeps it from wanting to
# write __pycache__ into that read-only tree.
RUN useradd --system --uid 10001 --no-create-home finances
USER finances
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

EXPOSE 8000
CMD ["uvicorn", "finances_ai.app:app", "--host", "0.0.0.0", "--port", "8000"]
