.DEFAULT_GOAL := help
COMPOSE := docker compose -f infra/docker-compose.yml --env-file .env
# Maven wrapper if it exists, otherwise a system mvn. Generate the wrapper once with:
#   cd services/api && mvn wrapper:wrapper
MVN := $(shell test -x services/api/mvnw && echo ./mvnw || echo mvn)

# Java 25 has to be found rather than assumed. A Homebrew openjdk is not registered with macOS's
# java_home unless it has been symlinked into /Library/Java/JavaVirtualMachines, so a plain
# `make test` on a fresh shell reported "Unable to locate a Java Runtime" while a perfectly good
# JDK sat in the Homebrew prefix. Ask macOS first, fall back to Homebrew, and leave an existing
# JAVA_HOME alone.
JAVA_HOME ?= $(shell \
	/usr/libexec/java_home -v 25 2>/dev/null \
	|| ls -d $$(brew --prefix 2>/dev/null)/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home 2>/dev/null \
	|| true)
export JAVA_HOME

.PHONY: help doctor ensure-env up down logs db api web ai test test-api test-web test-ai test-mcp \
	e2e e2e-down mcp mcp-token backup backup-key restore bundle fmt clean nuke

# Every one of these has cost a wasted run: a JDK Homebrew installed but macOS could not find, an
# nvm default two majors behind .nvmrc, Docker Desktop not running so Testcontainers failed with a
# message about sockets, a scratch stack still up from last week. One command, one screen.
doctor: ## Check the toolchain, the secrets, and which stacks are running
	@ok() { printf '  \033[32mok\033[0m    %s\n' "$$1"; }; bad() { printf '  \033[31mMISSING\033[0m %s\n' "$$1"; }; \
	echo "Toolchain"; \
	if test -n "$(JAVA_HOME)" && test -x "$(JAVA_HOME)/bin/java"; then ok "JDK 25 at $(JAVA_HOME)"; else bad "JDK 25 (brew install openjdk@25)"; fi; \
	if command -v uv >/dev/null 2>&1; then ok "uv $$(uv --version | awk '{print $$2}')"; else bad "uv (brew install uv)"; fi; \
	want=$$(cat .nvmrc 2>/dev/null); have=$$(. "$$NVM_DIR/nvm.sh" 2>/dev/null && nvm use >/dev/null 2>&1; node -v 2>/dev/null); \
	case "$$have" in v$$want.*) ok "node $$have (.nvmrc wants $$want)";; *) bad "node $$want via nvm (have $${have:-none})";; esac; \
	if docker info >/dev/null 2>&1; then ok "Docker daemon"; else bad "Docker daemon (open -a Docker); the Java suite needs it for Testcontainers"; fi; \
	echo "Configuration"; \
	if test -f .env; then ok ".env present"; else bad ".env (make ensure-env)"; fi; \
	if grep -qE '^ACCOUNT_KEY_SECRET=.+' .env 2>/dev/null; then ok "ACCOUNT_KEY_SECRET set (back .env up with the database)"; else bad "ACCOUNT_KEY_SECRET (make ensure-env)"; fi; \
	if grep -qE '^LOCAL_API_TOKEN=.+' .env 2>/dev/null; then ok "LOCAL_API_TOKEN set (Claude Code can drive the API)"; else printf '  \033[33m--\033[0m    LOCAL_API_TOKEN blank: the MCP server is off (make mcp-token)\n'; fi; \
	echo "Stacks"; \
	if docker info >/dev/null 2>&1; then \
		dev=$$(docker ps --filter name=finances-manager- --format '{{.Names}} {{.Status}} {{.Ports}}' | sed 's/^/  /'); \
		e2e=$$(docker ps --filter name=finances-e2e- --format '{{.Names}} {{.Status}} {{.Ports}}' | sed 's/^/  /'); \
		if test -n "$$dev"; then echo "  dev stack (finances-manager, holds real data):"; echo "$$dev"; else echo "  dev stack: down (make up)"; fi; \
		if test -n "$$e2e"; then echo "  scratch stack (finances-e2e, throwaway; make e2e-down disposes of it):"; echo "$$e2e"; else echo "  scratch stack: down"; fi; \
		if echo "$$e2e" | grep -q '0.0.0.0:'; then bad "the scratch stack is published on every interface"; fi; \
	fi; \
	echo "Repository"; \
	if git rev-parse --abbrev-ref @{upstream} >/dev/null 2>&1; then ok "branch has an upstream"; else printf '  \033[33m--\033[0m    branch %s has never been pushed; make bundle writes a copy to backups/\n' "$$(git rev-parse --abbrev-ref HEAD)"; fi; \
	latest=$$(ls -t backups/repo-*.bundle 2>/dev/null | head -1); if test -n "$$latest"; then ok "latest bundle $$latest"; else printf '  \033[33m--\033[0m    no repository bundle yet (make bundle)\n'; fi

# In-place edits to .env. `sed -i ''` is BSD-only: GNU sed reads the '' as the script and the
# expression as a file name, fails, and the recipe went on to print "Wrote ..." anyway — so on the
# Linux homelab `make mcp-token` succeeded and wrote nothing. perl behaves the same everywhere.
EDIT_IN_PLACE := perl -pi -e

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

# Secrets the stack cannot run without, generated rather than documented: nobody should have to
# know they exist. ACCOUNT_KEY_SECRET keys the one-way account ids the importer links rows by
# (docs/SECURITY.md); the AI service refuses to start without it. It must stay the same for the
# life of the database, so it is generated once and never rotated here.
ensure-env: ## Create .env from the example and fill in the generated secrets
	@test -f .env || cp .env.example .env
	@if ! grep -qE '^ACCOUNT_KEY_SECRET=.+' .env; then \
		secret=$$(openssl rand -hex 32) || exit 1; \
		if grep -q '^ACCOUNT_KEY_SECRET=' .env; then \
			$(EDIT_IN_PLACE) "s|^ACCOUNT_KEY_SECRET=.*|ACCOUNT_KEY_SECRET=$$secret|" .env; \
		else \
			printf '\nACCOUNT_KEY_SECRET=%s\n' "$$secret" >> .env; \
		fi; \
		grep -qE '^ACCOUNT_KEY_SECRET=.+' .env || { echo "could not write ACCOUNT_KEY_SECRET to .env"; exit 1; }; \
		echo "Generated ACCOUNT_KEY_SECRET in .env. Back .env up with the database: an account linked under one secret is not found under another."; \
	fi

up: ensure-env ## Start the whole stack (postgres + api + web + ai)
	$(COMPOSE) up --build -d
	@echo "web  → http://localhost:4200"
	@echo "api  → http://localhost:8080/actuator/health"

down: ## Stop the stack
	$(COMPOSE) down

logs: ## Tail logs from all services
	$(COMPOSE) logs -f

db: ensure-env ## Start PostgreSQL only (for running services natively)
	$(COMPOSE) up -d db

api: ## Run the Spring Boot API natively on :8080
	cd services/api && $(MVN) spring-boot:run

web: ## Run the Angular dev server on :4200
	cd services/web && npm start

ai: ensure-env ## Run the FastAPI service on :8000
	@# The service reads ACCOUNT_KEY_SECRET from its environment, which natively means .env.
	set -a && . ./.env && set +a && cd services/ai && uv run uvicorn finances_ai.app:app --reload --port 8000

test: test-api test-web test-ai test-mcp ## Run every test suite

test-api: ## Java tests
	@test -n "$(JAVA_HOME)" || { echo "No JDK 25 found. macOS: brew install openjdk@25. Linux: install a JDK 25 and export JAVA_HOME."; exit 1; }
	cd services/api && $(MVN) -B test

test-web: ## Angular tests
	@# Angular 22 needs a newer Node than nvm's default here, and the failure looks like a broken
	@# test run rather than a wrong shell. Use the version .nvmrc names, if nvm is installed.
	@. "$$NVM_DIR/nvm.sh" 2>/dev/null && nvm use >/dev/null 2>&1; \
		cd services/web && npm run test:ci

test-ai: ## Python tests
	cd services/ai && uv sync --extra dev -q && uv run pytest -q

test-mcp: ## MCP server tests
	cd services/mcp && uv run pytest -q

mcp: ## Run the MCP server by hand (Claude Code normally launches it itself)
	cd services/mcp && uv run finances-mcp

# Writes the token to .env rather than printing it for copying: a secret that has to be moved by
# hand ends up in a shell history, a note, or both.
mcp-token: ## Generate the local API token for Claude Code and write it to .env
	@test -f .env || cp .env.example .env
	@if grep -qE '^LOCAL_API_TOKEN=.+' .env; then \
		echo "LOCAL_API_TOKEN is already set in .env — leaving it alone."; \
		echo "Delete that line first if you want to rotate it."; \
	else \
		token=$$(openssl rand -base64 36 | tr -d '\n') || exit 1; \
		if grep -q '^LOCAL_API_TOKEN=' .env; then \
			$(EDIT_IN_PLACE) "s|^LOCAL_API_TOKEN=.*|LOCAL_API_TOKEN=$$token|" .env; \
		else \
			printf '\nLOCAL_API_TOKEN=%s\n' "$$token" >> .env; \
		fi; \
		grep -qE '^LOCAL_API_TOKEN=.+' .env || { echo "could not write LOCAL_API_TOKEN to .env"; exit 1; }; \
		echo "Wrote LOCAL_API_TOKEN to .env."; \
		echo "Now run: make up   (the API only reads it at startup)"; \
	fi

# The browser suite gets its own throwaway stack — a separate compose *project*, so Docker
# namespaces its containers, network and volumes away from the dev stack automatically. No second
# compose file and no duplicated YAML; only the published ports differ.
#
# This exists because the suite and the dev stack used to share one database, which is a hazard no
# guard fully removes: the tests truncate, and truncating a database somebody is using destroys
# real imported statements with no undo. It became a hard blocker the moment Allen created a real
# account — `/api/v1/setup` only works on a first run, so the suite could no longer create its own
# user at all without wiping his.
E2E_PROJECT  := finances-e2e
E2E_API_PORT := 8081
E2E_WEB_PORT := 4201
# Two env files: the real one for the parts that must match (database name/user), then
# infra/e2e.env on top, which blanks LOCAL_API_TOKEN and sets a throwaway password. The scratch
# stack used to inherit the production token wholesale and sit on :8081 accepting it.
# infra/e2e.compose.yml is layered last: it turns the restart policy off, so the scratch stack does
# not resurrect itself after a reboot the way the dev stack should.
E2E_COMPOSE  := API_PORT=$(E2E_API_PORT) WEB_PORT=$(E2E_WEB_PORT) \
	docker compose -p $(E2E_PROJECT) -f infra/docker-compose.yml -f infra/e2e.compose.yml \
	--env-file .env --env-file infra/e2e.env

e2e: ensure-env ## Browser tests on their own throwaway stack (never touches your dev data)
	@# A scratch stack with a blank token and an empty database is safe to show to another device
	@# on the home network, which is what the Cowork brief needs. It is not something to publish
	@# on every interface: that stayed up for a week once, restarting itself after every reboot.
	@case "$${BIND_ADDR:-127.0.0.1}" in 0.0.0.0|::|'*') \
		echo "BIND_ADDR=$$BIND_ADDR would publish the scratch stack on every interface."; \
		echo "Use this machine's LAN address instead: BIND_ADDR=\$$(ipconfig getifaddr en0) make e2e"; \
		exit 1;; \
	esac
	$(E2E_COMPOSE) up --build -d
	@. "$$NVM_DIR/nvm.sh" 2>/dev/null && nvm use >/dev/null 2>&1; \
		cd services/web \
		&& E2E_BASE_URL=http://localhost:$(E2E_WEB_PORT) \
		   E2E_API_URL=http://localhost:$(E2E_API_PORT) \
		   E2E_COMPOSE_PROJECT=$(E2E_PROJECT) \
		   npx playwright test
	@echo "(the e2e stack is still up on :$(E2E_WEB_PORT); make e2e-down disposes of it)"

e2e-down: ## Remove the browser-test stack and its database
	$(E2E_COMPOSE) down -v

# --- Backup and restore (D-16: a tested restore precedes real data) ---------------------------
#
# `make backup` writes a pg_dump custom-format archive to backups/ (gitignored). `make restore`
# loads an archive into the throwaway e2e project — never the dev stack — and then compares row
# counts, table by table, against the live database it came from. That comparison is the test:
# an archive that restores cleanly but is missing a table is exactly the failure "tested restore"
# is meant to catch, and it is invisible unless something counts.
#
# Encryption and an off-site copy are the deployment half of D-16 and are not done here; this is
# the part that has to work before either of those is worth doing.
BACKUP_DIR := backups
DB_EXEC    := $(COMPOSE) exec -T db
E2E_DB_EXEC := $(E2E_COMPOSE) exec -T db
# The tables to compare are read from the live database, not listed here. A list drifted: it
# named 14 tables while the migrations had made 19, and the five it lacked — the passkeys, the
# sessions, the schema notes — were never counted, so "every table matches" was the list agreeing
# with itself. Review 2026-09-11 S1.
LIVE_TABLES = $(DB_EXEC) psql -U $${DATABASE_USER:-finances} -d $${DATABASE_NAME:-finances} -tA \
	-c "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"

# Where the backup encryption key lives, and where finished archives are copied. Both come from
# .env (BACKUP_KEY_FILE, BACKUP_COPY_TO); the key defaults to a path outside the repository so a
# `git clean` or a stolen checkout cannot take the key with the archives. Read with grep rather
# than `include .env`: make would choke on values a shell reads fine.
env_value = $$(grep -E '^$(1)=' .env 2>/dev/null | head -1 | cut -d= -f2- | sed 's/^"//; s/"$$//')
BACKUP_KEY_DEFAULT := $(HOME)/.config/finances/backup.key
OPENSSL_ENC := openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt

backup-key: ## Generate the key that encrypts backups (kept outside the repo; put a copy in a password manager)
	@key=$(call env_value,BACKUP_KEY_FILE); key=$$(eval echo $${key:-$(BACKUP_KEY_DEFAULT)}); \
	if test -f "$$key"; then echo "A key already exists at $$key — leaving it alone. Delete it first to make a new one, and know that every archive encrypted with the old one becomes unreadable."; \
	else mkdir -p "$$(dirname "$$key")" && (umask 077 && openssl rand -base64 48 > "$$key") \
		&& echo "wrote $$key" \
		&& echo "Copy it into a password manager now. Without it every encrypted backup is noise; with it and an archive, everything comes back."; fi

# Encrypted when a key exists, plaintext with a loud warning when it does not; copied to
# BACKUP_COPY_TO when that is set. The dump alone is not a backup: ACCOUNT_KEY_SECRET in .env keys
# every import link, so .env rides inside the archive (docs/SECURITY.md, docs/RUNBOOK.md).
backup: ## Dump the dev database to backups/, encrypted and copied off-disk when configured
	@mkdir -p $(BACKUP_DIR)
	@stamp=$$(date +%Y%m%d-%H%M%S); dump=finances-$$stamp.dump; envcopy=env-$$stamp; \
	$(DB_EXEC) pg_dump -U $${DATABASE_USER:-finances} -d $${DATABASE_NAME:-finances} -Fc > $(BACKUP_DIR)/$$dump \
		|| { echo "pg_dump failed; is the dev stack up?"; rm -f $(BACKUP_DIR)/$$dump; exit 1; }; \
	cp .env $(BACKUP_DIR)/$$envcopy && chmod 600 $(BACKUP_DIR)/$$envcopy; \
	echo "wrote $(BACKUP_DIR)/$$dump ($$(du -h $(BACKUP_DIR)/$$dump | cut -f1)) and $(BACKUP_DIR)/$$envcopy"; \
	key=$(call env_value,BACKUP_KEY_FILE); key=$$(eval echo $${key:-$(BACKUP_KEY_DEFAULT)}); \
	if test -f "$$key"; then \
		enc=$(BACKUP_DIR)/finances-$$stamp.enc; \
		tar -c -C $(BACKUP_DIR) $$dump $$envcopy | $(OPENSSL_ENC) -pass file:"$$key" -out $$enc \
			&& rm -f $(BACKUP_DIR)/$$dump $(BACKUP_DIR)/$$envcopy \
			&& echo "encrypted to $$enc ($$(du -h $$enc | cut -f1)); plaintext removed"; \
		dest=$(call env_value,BACKUP_COPY_TO); dest=$$(eval echo $$dest); \
		if test -n "$$dest"; then mkdir -p "$$dest" && cp $$enc "$$dest/" && echo "copied to $$dest/"; \
		else echo "BACKUP_COPY_TO is not set in .env: this archive exists on this disk only."; fi; \
	else \
		echo "UNENCRYPTED. No key at $$key — run 'make backup-key', then back up again. Until then $(BACKUP_DIR)/ holds the ledger and .env in the clear."; \
	fi

# The branch has never been pushed, so until it is, this Mac is the only copy of everything since
# 2026-08-21. A bundle is the whole repository in one file; copy it to another disk. Pushing is
# Allen's call and this does not do it.
bundle: ## Write the whole repository, every branch and tag, to backups/repo-<timestamp>.bundle
	@mkdir -p $(BACKUP_DIR)
	@f=$(BACKUP_DIR)/repo-$$(date +%Y%m%d-%H%M%S).bundle; \
		git bundle create $$f --all && git bundle verify $$f >/dev/null \
		&& echo "wrote $$f ($$(du -h $$f | cut -f1)). Restore anywhere with: git clone $$f finances_manager"

# Accepts a plain .dump or an encrypted .enc archive (decrypted to a private temp dir first, and
# the temp dir removed afterwards). Runs against the scratch project only — DB_EXEC (dev) is used
# solely to read row counts for the comparison.
restore: ## Restore FILE=backups/x.dump or x.enc into the scratch stack and verify row counts against dev
	@test -n "$(FILE)" || { echo "usage: make restore FILE=backups/finances-....dump (or .enc)"; exit 1; }
	@test -f "$(FILE)" || { echo "no such file: $(FILE)"; exit 1; }
	@dump="$(FILE)"; work=""; \
	case "$(FILE)" in *.enc) \
		key=$(call env_value,BACKUP_KEY_FILE); key=$$(eval echo $${key:-$(BACKUP_KEY_DEFAULT)}); \
		test -f "$$key" || { echo "no key at $$key — set BACKUP_KEY_FILE in .env to where the key is, or restore it from your password manager"; exit 1; }; \
		work=$$(mktemp -d) && chmod 700 "$$work" \
			&& $(OPENSSL_ENC) -d -pass file:"$$key" -in "$(FILE)" | tar -x -C "$$work" \
			|| { rm -rf "$$work"; echo "could not decrypt $(FILE): wrong key, or a damaged archive"; exit 1; }; \
		dump=$$(ls "$$work"/*.dump); echo "decrypted $(FILE); the archive also holds $$(ls "$$work" | grep '^env-' || echo 'no .env')";; \
	esac; \
	$(E2E_COMPOSE) up -d db; \
	until $(E2E_DB_EXEC) pg_isready -q -U $${DATABASE_USER:-finances}; do sleep 1; done; \
	$(E2E_DB_EXEC) psql -U $${DATABASE_USER:-finances} -d postgres -q \
		-c "DROP DATABASE IF EXISTS $${DATABASE_NAME:-finances}_restore" \
		-c "CREATE DATABASE $${DATABASE_NAME:-finances}_restore"; \
	cat "$$dump" | $(E2E_DB_EXEC) pg_restore -U $${DATABASE_USER:-finances} \
		-d $${DATABASE_NAME:-finances}_restore --no-owner --no-privileges; \
	test -z "$$work" || rm -rf "$$work"; \
	echo "restored. verifying row counts (live vs restored):"; fail=0; \
	tables=$$($(LIVE_TABLES)); \
	test -n "$$tables" || { echo "could not list the live database's tables"; exit 1; }; \
	echo "  $$(echo $$tables | wc -w | tr -d ' ') tables in the live database"; \
	for t in $$tables; do \
		live=$$($(DB_EXEC) psql -U $${DATABASE_USER:-finances} -d $${DATABASE_NAME:-finances} -tA -c "SELECT count(*) FROM $$t"); \
		got=$$($(E2E_DB_EXEC) psql -U $${DATABASE_USER:-finances} -d $${DATABASE_NAME:-finances}_restore -tA -c "SELECT count(*) FROM $$t"); \
		if [ "$$live" = "$$got" ]; then printf "  %-16s %6s ok\n" $$t $$got; \
		else printf "  %-16s live=%s restored=%s MISMATCH\n" $$t $$live $$got; fail=1; fi; \
	done; \
	$(E2E_DB_EXEC) psql -U $${DATABASE_USER:-finances} -d postgres -q -c "DROP DATABASE $${DATABASE_NAME:-finances}_restore"; \
	if [ $$fail -ne 0 ]; then echo "RESTORE VERIFICATION FAILED"; exit 1; fi; \
	echo "restore verified: every table matches."

fmt: ## Format everything
	cd services/web && npm run format
	cd services/ai && uv run ruff format src tests && uv run ruff check --fix src tests
	cd services/mcp && uv run ruff format src tests && uv run ruff check --fix src tests

clean: ## Remove build output and stop containers (keeps the database)
	$(COMPOSE) down
	rm -rf services/web/dist services/web/node_modules services/api/target \
		services/ai/.venv services/mcp/.venv

nuke: ## Like clean, and DELETES the dev database volume. Asks first.
	@printf "This deletes the dev database volume. Type 'delete' to continue: "; read ans; \
		[ "$$ans" = "delete" ] || { echo "aborted"; exit 1; }
	$(COMPOSE) down -v
	rm -rf services/web/dist services/web/node_modules services/api/target \
		services/ai/.venv services/mcp/.venv
