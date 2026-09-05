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

.PHONY: help up down logs db api web ai test test-api test-web test-ai test-mcp e2e e2e-down \
	mcp mcp-token backup restore fmt clean nuke

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start the whole stack (postgres + api + web + ai)
	@test -f .env || cp .env.example .env
	$(COMPOSE) up --build -d
	@echo "web  → http://localhost:4200"
	@echo "api  → http://localhost:8080/actuator/health"

down: ## Stop the stack
	$(COMPOSE) down

logs: ## Tail logs from all services
	$(COMPOSE) logs -f

db: ## Start PostgreSQL only (for running services natively)
	@test -f .env || cp .env.example .env
	$(COMPOSE) up -d db

api: ## Run the Spring Boot API natively on :8080
	cd services/api && $(MVN) spring-boot:run

web: ## Run the Angular dev server on :4200
	cd services/web && npm start

ai: ## Run the FastAPI service on :8000
	cd services/ai && uv run uvicorn finances_ai.app:app --reload --port 8000

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
		token=$$(openssl rand -base64 36 | tr -d '\n'); \
		if grep -q '^LOCAL_API_TOKEN=' .env; then \
			sed -i '' "s|^LOCAL_API_TOKEN=.*|LOCAL_API_TOKEN=$$token|" .env; \
		else \
			printf '\nLOCAL_API_TOKEN=%s\n' "$$token" >> .env; \
		fi; \
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
E2E_COMPOSE  := API_PORT=$(E2E_API_PORT) WEB_PORT=$(E2E_WEB_PORT) \
	docker compose -p $(E2E_PROJECT) -f infra/docker-compose.yml --env-file .env --env-file infra/e2e.env

e2e: ## Browser tests on their own throwaway stack (never touches your dev data)
	@test -f .env || cp .env.example .env
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
TABLES     := app_user ledger_entity institution connection account category import_batch \
	transaction target statement categorization security holding login_attempt

backup: ## Dump the dev database to backups/finances-<timestamp>.dump
	@mkdir -p $(BACKUP_DIR)
	@f=$(BACKUP_DIR)/finances-$$(date +%Y%m%d-%H%M%S).dump; \
		$(DB_EXEC) pg_dump -U $${DATABASE_USER:-finances} -d $${DATABASE_NAME:-finances} -Fc > $$f \
		&& echo "wrote $$f ($$(du -h $$f | cut -f1))"

restore: ## Restore FILE=backups/x.dump into the scratch stack and verify row counts against dev
	@test -n "$(FILE)" || { echo "usage: make restore FILE=backups/finances-....dump"; exit 1; }
	@test -f "$(FILE)" || { echo "no such file: $(FILE)"; exit 1; }
	$(E2E_COMPOSE) up -d db
	@until $(E2E_DB_EXEC) pg_isready -q -U $${DATABASE_USER:-finances}; do sleep 1; done
	@# A clean target: drop and recreate so a stale scratch database cannot mask a missing table.
	@$(E2E_DB_EXEC) psql -U $${DATABASE_USER:-finances} -d postgres -q \
		-c "DROP DATABASE IF EXISTS $${DATABASE_NAME:-finances}_restore" \
		-c "CREATE DATABASE $${DATABASE_NAME:-finances}_restore"
	@cat "$(FILE)" | $(E2E_DB_EXEC) pg_restore -U $${DATABASE_USER:-finances} \
		-d $${DATABASE_NAME:-finances}_restore --no-owner --no-privileges
	@echo "restored. verifying row counts (live vs restored):"; fail=0; \
	for t in $(TABLES); do \
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
