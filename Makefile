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
	mcp mcp-token fmt clean

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## Start the whole stack (postgres + api + web + ai)
	@test -f .env || cp .env.example .env
	$(COMPOSE) up --build -d
	@echo "web  → http://localhost:4200"
	@echo "api  → http://localhost:8080/api/v1/status"

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
	@test -n "$(JAVA_HOME)" || { echo "No JDK 25 found. brew install openjdk@25"; exit 1; }
	cd services/api && $(MVN) -B test

test-web: ## Angular tests
	@# Angular 22 needs a newer Node than nvm's default here, and the failure looks like a broken
	@# test run rather than a wrong shell. Use the version .nvmrc names, if nvm is installed.
	@. "$$NVM_DIR/nvm.sh" 2>/dev/null && nvm use >/dev/null 2>&1; \
		cd services/web && npm run test:ci

test-ai: ## Python tests
	cd services/ai && uv run pytest -q

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
E2E_COMPOSE  := API_PORT=$(E2E_API_PORT) WEB_PORT=$(E2E_WEB_PORT) \
	docker compose -p $(E2E_PROJECT) -f infra/docker-compose.yml --env-file .env

e2e: ## Browser tests on their own throwaway stack (never touches your dev data)
	@test -f .env || cp .env.example .env
	$(E2E_COMPOSE) up --build -d
	@. "$$NVM_DIR/nvm.sh" 2>/dev/null && nvm use >/dev/null 2>&1; \
		cd services/web \
		&& E2E_BASE_URL=http://localhost:$(E2E_WEB_PORT) \
		   E2E_API_URL=http://localhost:$(E2E_API_PORT) \
		   E2E_COMPOSE_PROJECT=$(E2E_PROJECT) \
		   npx playwright test

e2e-down: ## Remove the browser-test stack and its database
	$(E2E_COMPOSE) down -v

fmt: ## Format everything
	cd services/web && npm run format
	cd services/ai && uv run ruff format src tests && uv run ruff check --fix src tests
	cd services/mcp && uv run ruff format src tests && uv run ruff check --fix src tests

clean: ## Remove build output and containers
	$(COMPOSE) down -v
	rm -rf services/web/dist services/web/node_modules services/api/target \
		services/ai/.venv services/mcp/.venv
