"""Reads the repo's .env so the token lives in exactly one place.

Claude Code launches this server as a subprocess, which inherits whatever environment the editor
happened to have — not the shell where `.env` means something. Without this, using the MCP server
would mean exporting `LOCAL_API_TOKEN` from a shell profile, which is a second copy of a secret
that then has to be kept in step with the first.

Deliberately hand-rolled rather than pulling in python-dotenv: it is twenty lines, and a dependency
that runs at startup and can read arbitrary files is not a small thing to add for that.
"""

from __future__ import annotations

import os
from pathlib import Path


def find_env_file(start: Path | None = None) -> Path | None:
    """Walks up from `start` looking for a .env, stopping at the filesystem root."""
    current = (start or Path.cwd()).resolve()
    for directory in [current, *current.parents]:
        candidate = directory / ".env"
        if candidate.is_file():
            return candidate
    return None


def parse(text: str) -> dict[str, str]:
    """Parses KEY=VALUE lines, ignoring comments and blanks.

    Handles the `export KEY=value` form and strips one layer of matching quotes, which is what
    people actually write in a .env. Anything more elaborate is a shell script, not configuration.
    """
    values: dict[str, str] = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        if line.startswith("export "):
            line = line[len("export ") :].lstrip()

        key, _, value = line.partition("=")
        key = key.strip()
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if key:
            values[key] = value
    return values


def load(start: Path | None = None) -> Path | None:
    """Loads .env into the environment without overwriting anything already set.

    Existing variables win, so an explicitly exported value — or one Claude Code passes in — is
    never silently replaced by a stale file.
    """
    path = find_env_file(start)
    if path is None:
        return None
    for key, value in parse(path.read_text(encoding="utf-8")).items():
        os.environ.setdefault(key, value)
    return path
