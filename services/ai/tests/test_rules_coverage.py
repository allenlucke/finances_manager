"""The measured baseline for the rules tier (M3, D-22): how many rows of each synthetic statement
the rules alone can categorize. Not accuracy — the fixtures are small and synthetic — but the
number the roadmap asked for, pinned so a rule change that loses coverage fails a test rather
than a feeling. The history tier (in the API) adds whatever the person has already decided.

Run with ``-s`` to see the table.
"""

from pathlib import Path

import pytest

from finances_ai.categorize import categorize
from finances_ai.ingest import parse_csv, parse_ofx

FIXTURES = Path(__file__).parent

# (file, rows the rules must categorize, rows that are not transfers). Raise a floor when a rule
# earns it; never lower one without saying why in the commit.
BASELINE = [
    ("sample.ofx", 4, 4),
    ("cacu.csv", 2, 5),
]


def _rows(name: str):
    path = FIXTURES / name
    parse = parse_ofx if path.suffix == ".ofx" else parse_csv
    result = parse(path.read_bytes(), "acct")
    return [t for t in result.transactions if not t.is_probable_transfer]


@pytest.mark.parametrize(("name", "floor", "expected_rows"), BASELINE)
def test_the_rules_tier_covers_at_least_the_measured_baseline(name, floor, expected_rows, capsys):
    rows = _rows(name)
    assert len(rows) == expected_rows, "the fixture changed; re-measure the baseline"

    suggestions = categorize(rows)
    hits = [s for s in suggestions if s.category]
    misses = [t.description for t, s in zip(rows, suggestions, strict=True) if not s.category]

    with capsys.disabled():
        print(
            f"\nrules tier on {name}: {len(hits)}/{len(rows)} categorized; uncategorized: {misses}"
        )
    assert len(hits) >= floor, (
        f"{name}: rules now cover {len(hits)} of {len(rows)}, below the {floor} measured"
    )
    # A suggestion of nothing is never a row: every miss says so as method "none".
    assert all(s.method == "none" for s in suggestions if not s.category)
