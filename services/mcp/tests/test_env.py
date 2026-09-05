"""Reading the token out of .env (D-17)."""

from finances_mcp import env


def test_parses_the_forms_people_actually_write():
    parsed = env.parse(
        """
        # a comment
        LOCAL_API_TOKEN=plain-value
        export EXPORTED=exported-value
        QUOTED="double quoted"
        SINGLE='single quoted'
        SPACED = spaced out

        NOT_A_PAIR
        """
    )

    assert parsed["LOCAL_API_TOKEN"] == "plain-value"
    assert parsed["EXPORTED"] == "exported-value"
    assert parsed["QUOTED"] == "double quoted"
    assert parsed["SINGLE"] == "single quoted"
    assert parsed["SPACED"] == "spaced out"
    assert "NOT_A_PAIR" not in parsed


def test_a_value_containing_equals_survives_intact():
    # Base64 tokens end in '=' padding often enough that splitting on every '=' would corrupt them.
    parsed = env.parse("LOCAL_API_TOKEN=abc==")

    assert parsed["LOCAL_API_TOKEN"] == "abc=="


def test_the_environment_wins_over_the_file(tmp_path, monkeypatch):
    (tmp_path / ".env").write_text("LOCAL_API_TOKEN=from-file\n")
    monkeypatch.setenv("LOCAL_API_TOKEN", "already-set")

    env.load(tmp_path)

    # Otherwise a stale file would silently override a value deliberately passed in.
    assert env.os.environ["LOCAL_API_TOKEN"] == "already-set"


def test_it_finds_a_dot_env_further_up_the_tree(tmp_path, monkeypatch):
    (tmp_path / ".env").write_text("LOCAL_API_TOKEN=from-the-root\n")
    nested = tmp_path / "services" / "mcp" / "src"
    nested.mkdir(parents=True)
    monkeypatch.delenv("LOCAL_API_TOKEN", raising=False)

    found = env.load(nested)

    assert found == tmp_path / ".env"
    assert env.os.environ["LOCAL_API_TOKEN"] == "from-the-root"


def test_no_dot_env_anywhere_is_not_an_error(tmp_path):
    assert env.load(tmp_path) is None


def test_only_the_keys_this_process_needs_are_loaded(tmp_path, monkeypatch):
    """The whole file used to be imported — the Postgres password with it — into a long-lived
    process that every subprocess inherits."""
    (tmp_path / ".env").write_text("LOCAL_API_TOKEN=t\nDATABASE_PASSWORD=secret\nAPI_URL=u\n")
    for key in ("LOCAL_API_TOKEN", "DATABASE_PASSWORD", "API_URL"):
        monkeypatch.delenv(key, raising=False)

    env.load(tmp_path)

    assert env.os.environ["LOCAL_API_TOKEN"] == "t"
    assert env.os.environ["API_URL"] == "u"
    assert "DATABASE_PASSWORD" not in env.os.environ


def test_the_search_stops_at_the_repository(tmp_path, monkeypatch):
    """A .env in a shared parent directory must not be picked up through the repo boundary."""
    (tmp_path / ".env").write_text("LOCAL_API_TOKEN=from-above-the-repo\n")
    repo = tmp_path / "repo"
    (repo / "services" / "mcp").mkdir(parents=True)
    (repo / ".git").mkdir()
    monkeypatch.delenv("LOCAL_API_TOKEN", raising=False)

    found = env.load(repo / "services" / "mcp")

    assert found is None
    assert "LOCAL_API_TOKEN" not in env.os.environ
