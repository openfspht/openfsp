PGSQL_DIR ?= $(HOME)/services/pgsql
DB_NAME ?= openfsp
DB_USER ?= openfsp
DB_PASSWORD ?= openfsp

PGSQL := docker compose --project-directory $(PGSQL_DIR) -f $(PGSQL_DIR)/compose.yml
PSQL := $(PGSQL) exec -T db psql -U postgres -v ON_ERROR_STOP=1 -tA
GRADLE := ./gradlew
FRONTEND := frontend
CONCURRENTLY := $(FRONTEND)/node_modules/.bin/concurrently

export OPENFSP_ADMIN_TOKEN ?= $$argon2id$$v=19$$m=65536,t=3,p=4$$PEua5g9jsR2Ot2g7bhkBWT4vxejdDAl8jlbiptXsNzI$$ZlQNIj02jEudsOtGDc/XRMcHIW2jOfSAzq/68WI+x5c
export OPENFSP_BOOTSTRAP_KEY ?= ofsp_test_DemoShopKeyPublishedForTheSimulator01
export OPENFSP_API_KEY ?= $(OPENFSP_BOOTSTRAP_KEY)
export SPRING_DATASOURCE_URL ?= jdbc:postgresql://127.0.0.1:5432/$(DB_NAME)
export SPRING_DATASOURCE_USERNAME ?= $(DB_USER)
export SPRING_DATASOURCE_PASSWORD ?= $(DB_PASSWORD)

.DEFAULT_GOAL := help
.PHONY: help install db db-shell db-reset dev dev-sandbox gateway gateway-sandbox simulator console simulator-ui shop hash build test lint typecheck format check demo clean

help:
	@grep -E '^[a-z-]+:' $(MAKEFILE_LIST) | cut -d: -f1 | grep -v '^help$$' | tr '\n' ' '; echo

install:
	bun install --cwd $(FRONTEND)

db:
	$(PGSQL) up -d --wait db
	@$(PSQL) -c "select 1 from pg_roles where rolname = '$(DB_USER)'" | grep -q 1 || $(PSQL) -c "create role $(DB_USER) login password '$(DB_PASSWORD)'"
	@$(PSQL) -c "select 1 from pg_database where datname = '$(DB_NAME)'" | grep -q 1 || $(PSQL) -c "create database $(DB_NAME) owner $(DB_USER)"

db-shell: db
	$(PGSQL) exec db psql -U $(DB_USER) -d $(DB_NAME)

db-reset: db
	$(PSQL) -c "drop database if exists $(DB_NAME) with (force)"
	$(PSQL) -c "create database $(DB_NAME) owner $(DB_USER)"

dev: db
	$(CONCURRENTLY) -k -c "#93c5fd,#c4b5fd,#fb7185,#fdba74,#86efac" -n gateway,simulator,console,simulator-ui,shop \
		"$(MAKE) --no-print-directory gateway" "$(MAKE) --no-print-directory simulator" "$(MAKE) --no-print-directory console" \
		"$(MAKE) --no-print-directory simulator-ui" "$(MAKE) --no-print-directory shop"

dev-sandbox: db
	$(CONCURRENTLY) -k -c "#93c5fd,#c4b5fd,#fb7185,#fdba74,#86efac" -n gateway,simulator,console,simulator-ui,shop \
		"$(MAKE) --no-print-directory gateway-sandbox" "$(MAKE) --no-print-directory simulator" "$(MAKE) --no-print-directory console" \
		"$(MAKE) --no-print-directory simulator-ui" "$(MAKE) --no-print-directory shop"

gateway:
	$(GRADLE) :gateway:infrastructure:bootRun --console=plain

gateway-sandbox:
	SPRING_PROFILES_ACTIVE=development,sandbox $(GRADLE) :gateway:infrastructure:bootRun --console=plain

simulator:
	$(GRADLE) :simulator:infrastructure:bootRun --console=plain

console:
	bun --cwd $(FRONTEND)/apps/console dev

simulator-ui:
	bun --cwd $(FRONTEND)/apps/simulator dev

shop:
	bun --cwd $(FRONTEND)/apps/shop dev

hash:
	@$(GRADLE) -q :gateway:infrastructure:bootJar
	@java -jar gateway/infrastructure/build/libs/app.jar hash

build:
	$(GRADLE) build
	bun --cwd $(FRONTEND) run build

test:
	$(GRADLE) test
	bun --cwd $(FRONTEND) run test

lint:
	bun --cwd $(FRONTEND) run lint

typecheck:
	bun --cwd $(FRONTEND) run typecheck

format:
	bun --cwd $(FRONTEND) run format

check: build lint typecheck test

demo:
	docker compose --profile demo up --build

clean:
	$(GRADLE) clean
	rm -rf $(FRONTEND)/apps/*/dist
