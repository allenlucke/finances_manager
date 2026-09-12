"""Shared test setup.

The account key is an HMAC under a per-install secret (docs/SECURITY.md). The suite sets a
throwaway one so the readers can derive keys; the assertions that matter are that a key is stable
under one secret and different under another — see test_common.
"""

import os

os.environ.setdefault("ACCOUNT_KEY_SECRET", "test-secret-not-for-any-real-install")
