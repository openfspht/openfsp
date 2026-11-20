BACKEND := backend
FRONTEND := frontend
SDK_TYPESCRIPT := sdks/typescript
GRADLE := ./$(BACKEND)/gradlew -p $(BACKEND)
MULTIPLEX := $(FRONTEND)/node_modules/.bin/multiplex --title OpenFSP
RUN := make --no-print-directory
LOAD_KEYS := set -a && . ./.env && set +a &&
VALIDATOR := $(BACKEND)/validator/infrastructure/build
VALIDATE := java -jar $(VALIDATOR)/libs/app.jar validate

TARGET ?= http://127.0.0.1:8080
REPORT ?= $(VALIDATOR)/conformance-report.json
WEBHOOK_PORT ?= 8099

.DEFAULT_GOAL := help
.PHONY: help keys install dev dev-sandbox gateway gateway-sandbox gateway-conformance simulator simulator-conformance console simulator-ui validator validator-ui hash validate check demo clean

help:
	@grep -E '^[a-z -]+:' $(firstword $(MAKEFILE_LIST)) | cut -d: -f1 | grep -v '^help$$' | tr '\n' ' '; echo

keys:
	@[ -f .env ] || { key="$$(openssl rand -base64 32)" && umask 077 && printf 'OPENFSP_APP_KEY=%s\n' "$$key" > .env; }

install:
	bun install --cwd $(FRONTEND)
	bun install --cwd $(SDK_TYPESCRIPT)

dev: keys
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui' 'validator@#fde047,$(RUN) validator' 'validator-ui@#5eead4,$(RUN) validator-ui'

dev-sandbox: keys
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway-sandbox' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui'

gateway: keys
	$(LOAD_KEYS) SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-development,simulated,demo} $(GRADLE) :$@:infrastructure:bootRun --console=plain

simulator:
	SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-development} $(GRADLE) :$@:infrastructure:bootRun --console=plain

validator:
	SPRING_PROFILES_ACTIVE=$${SPRING_PROFILES_ACTIVE:-development} $(GRADLE) :$@:infrastructure:bootRun --console=plain

gateway-sandbox: keys
	set -a; [ ! -f .env.sandbox ] || . ./.env.sandbox; set +a; SPRING_PROFILES_ACTIVE=development,simulated,sandbox,demo $(RUN) gateway

gateway-conformance: keys
	SPRING_PROFILES_ACTIVE=development,simulated,conformance,demo $(RUN) gateway

simulator-conformance:
	SPRING_PROFILES_ACTIVE=development,conformance $(RUN) simulator

console simulator-ui validator-ui:
	bun run --cwd $(FRONTEND)/apps/$(@:-ui=) dev

hash:
	@$(GRADLE) -q :gateway:infrastructure:bootJar
	@java -jar $(BACKEND)/gateway/infrastructure/build/libs/app.jar hash

validate:
	@$(GRADLE) -q :validator:infrastructure:bootJar
	@$(VALIDATE) --base-url $(TARGET) --webhook-port $(WEBHOOK_PORT) --report $(REPORT)

check:
	$(GRADLE) build
	bun run --cwd $(FRONTEND) build
	bun run --cwd $(FRONTEND) lint
	bun run --cwd $(FRONTEND) typecheck
	bun run --cwd $(FRONTEND) test
	cd $(FRONTEND) && bunx prettier --check .
	bun run --cwd $(SDK_TYPESCRIPT) check
	bun run --cwd $(SDK_TYPESCRIPT) test
	bun run --cwd $(SDK_TYPESCRIPT) build
	REDOCLY_TELEMETRY=off bunx @redocly/cli@2.72.1 lint --config openapi/redocly.yaml openapi/openapi.yaml
	uvx sqlfluff@3.4.2 lint $(BACKEND)/*/infrastructure/src/main/resources/db/migration

demo: keys
	docker compose --profile demo up --build

clean:
	$(GRADLE) clean
	rm -rf $(FRONTEND)/apps/*/dist
