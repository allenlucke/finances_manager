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
