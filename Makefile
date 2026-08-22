.DEFAULT_GOAL := help
COMPOSE := docker compose -f infra/docker-compose.yml --env-file .env
# Maven wrapper if it exists, otherwise a system mvn. Generate the wrapper once with:
#   cd services/api && mvn wrapper:wrapper
MVN := $(shell test -x services/api/mvnw && echo ./mvnw || echo mvn)

.PHONY: help up down logs db api web ai test test-api test-web test-ai fmt clean

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

test: test-api test-web test-ai ## Run every test suite

test-api: ## Java tests
	cd services/api && $(MVN) -B test

test-web: ## Angular tests
	cd services/web && npm run test:ci

test-ai: ## Python tests
	cd services/ai && uv run pytest -q

fmt: ## Format everything
	cd services/web && npm run format
	cd services/ai && uv run ruff format src tests && uv run ruff check --fix src tests

clean: ## Remove build output and containers
	$(COMPOSE) down -v
	rm -rf services/web/dist services/web/node_modules services/api/target services/ai/.venv
