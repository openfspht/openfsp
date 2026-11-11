DB_NAME ?= openfsp
DB_USER ?= openfsp
DB_PASSWORD ?= openfsp

BACKEND := backend
GRADLE := ./$(BACKEND)/gradlew -p $(BACKEND)
FRONTEND := frontend
MULTIPLEX := $(FRONTEND)/node_modules/.bin/multiplex --title OpenFSP
RUN := make --no-print-directory

export OPENFSP_ADMIN_TOKEN ?= $$argon2id$$v=19$$m=65536,t=3,p=4$$PEua5g9jsR2Ot2g7bhkBWT4vxejdDAl8jlbiptXsNzI$$ZlQNIj02jEudsOtGDc/XRMcHIW2jOfSAzq/68WI+x5c
export OPENFSP_BOOTSTRAP_KEY ?= ofsp_test_DemoShopKeyPublishedForTheSimulator01
export OPENFSP_API_KEY ?= $(OPENFSP_BOOTSTRAP_KEY)
export SPRING_DATASOURCE_URL ?= jdbc:postgresql://127.0.0.1:5432/$(DB_NAME)
export SPRING_DATASOURCE_USERNAME ?= $(DB_USER)
export SPRING_DATASOURCE_PASSWORD ?= $(DB_PASSWORD)
export OPENFSP_DERIVATION_KEY ?= ZGV2ZWxvcG1lbnQtb25seS1kZXJpdmF0aW9uLWtleS0wMQ==

VALIDATE_BASE_URL ?= http://127.0.0.1:8080
VALIDATE_SIMULATOR_URL ?= http://127.0.0.1:8090
VALIDATE_REPORT ?= $(BACKEND)/validator/build/conformance-report.json
VALIDATE_ADMIN_TOKEN ?= demo-admin-token
VALIDATE_RESTRICTED_KEY := {"principal":"conformance-restricted","scopes":["payments:read","payments:write"]}

.DEFAULT_GOAL := help
.PHONY: help install dev dev-sandbox gateway gateway-sandbox simulator console simulator-ui shop validator-web validator-ui hash validate build test lint typecheck format check demo clean

help:
	@grep -E '^[a-z-]+:' $(MAKEFILE_LIST) | cut -d: -f1 | grep -v '^help$$' | tr '\n' ' '; echo

install:
	bun install --cwd $(FRONTEND)

dev:
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui' 'shop@#86efac,$(RUN) shop' \
		'validator-web@#fde047,$(RUN) validator-web' 'validator-ui@#5eead4,$(RUN) validator-ui'

dev-sandbox:
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway-sandbox' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui' 'shop@#86efac,$(RUN) shop'

gateway:
	$(GRADLE) :gateway:infrastructure:bootRun --console=plain

gateway-sandbox:
	set -a; [ ! -f .env.sandbox ] || . ./.env.sandbox; set +a; SPRING_PROFILES_ACTIVE=development,sandbox $(GRADLE) :gateway:infrastructure:bootRun --console=plain

simulator:
	$(GRADLE) :simulator:infrastructure:bootRun --console=plain

console:
	bun --cwd $(FRONTEND)/apps/console dev

simulator-ui:
	bun --cwd $(FRONTEND)/apps/simulator dev

shop:
	bun --cwd $(FRONTEND)/apps/shop dev

validator-web:
	$(GRADLE) :validator-web:bootRun --console=plain

validator-ui:
	bun --cwd $(FRONTEND)/apps/validator dev

hash:
	@$(GRADLE) -q :gateway:infrastructure:bootJar
	@java -jar $(BACKEND)/gateway/infrastructure/build/libs/app.jar hash

validate:
	@$(GRADLE) -q :validator:cliJar
	@session=$$(curl -fsS -o /dev/null -D - -H 'Content-Type: application/json' -d '{"token":"$(VALIDATE_ADMIN_TOKEN)"}' \
		$(VALIDATE_BASE_URL)/admin/session | sed -n 's/^[Ss]et-[Cc]ookie: *\(openfsp_admin=[^;]*\).*/\1/p') && \
	issued=$$(curl -fsS -H "Cookie: $$session" -H 'Content-Type: application/json' -d '$(VALIDATE_RESTRICTED_KEY)' $(VALIDATE_BASE_URL)/admin/keys) && \
	restricted=$$(printf '%s' "$$issued" | sed -n 's/.*"secret":"\([^"]*\)".*/\1/p') && \
	key=$$(printf '%s' "$$issued" | sed -n 's/.*"id":"\([^"]*\)".*/\1/p') && \
	test -n "$$restricted" && test -n "$$key" && { \
	java -jar $(BACKEND)/validator/build/libs/openfsp-validate.jar --base-url $(VALIDATE_BASE_URL) --simulator-url $(VALIDATE_SIMULATOR_URL) \
		--conformance conformance --report $(VALIDATE_REPORT) --input "restricted-key=$$restricted"; status=$$?; \
	curl -fsS -o /dev/null -X POST -H "Cookie: $$session" $(VALIDATE_BASE_URL)/admin/keys/$$key/revoke || status=$$?; \
	exit $$status; }

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
