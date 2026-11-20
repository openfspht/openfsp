BACKEND := backend
FRONTEND := frontend
SDK := sdks/typescript
GRADLE := ./$(BACKEND)/gradlew -p $(BACKEND)
JAR = $(BACKEND)/$(1)/infrastructure/build/libs/app.jar
RUN := $(MAKE) --no-print-directory
MULTIPLEX := $(FRONTEND)/node_modules/.bin/multiplex --title OpenFSP

TARGET ?= http://127.0.0.1:8080
REPORT ?= $(BACKEND)/validator/infrastructure/build/conformance-report.json
WEBHOOK_PORT ?= 8099

PROFILES := development
GATEWAY_PROFILES := development,simulated,demo
gateway-sandbox: GATEWAY_PROFILES := development,simulated,sandbox
gateway-sandbox: export OPENFSP_CONFORMANCE_ENABLED := false
gateway-conformance: GATEWAY_PROFILES := development,simulated,conformance,demo
simulator-conformance: PROFILES := development,conformance

.DEFAULT_GOAL := help
.PHONY: help keys install dev dev-sandbox gateway gateway-sandbox gateway-conformance simulator simulator-conformance validator console simulator-ui validator-ui hash validate check check-backend check-frontend check-sdk check-specs demo clean

help:
	@grep -E '^[a-z -]+:' $(firstword $(MAKEFILE_LIST)) | grep -v ':=' | cut -d: -f1 | grep -v -x -E 'help|check-.*' | tr '\n' ' '; echo

keys:
	@grep -qs '^OPENFSP_APP_KEY=.' .env || ( set -e; umask 077; tries=0; \
		until mkdir .env.lock 2>/dev/null; do tries=$$((tries + 1)); [ $$tries -lt 100 ] || { echo ".env.lock is held: remove it if no make keys runs" >&2; exit 1; }; sleep 0.1; done; \
		trap 'rmdir .env.lock' EXIT; grep -qs '^OPENFSP_APP_KEY=.' .env && exit 0; \
		key="$$(openssl rand -base64 32)"; [ -n "$$key" ]; source=.env; [ -f .env ] || source=.env.example; tmp="$$(mktemp .env.XXXXXX)"; \
		{ echo "OPENFSP_APP_KEY=$$key"; grep -v '^OPENFSP_APP_KEY=' "$$source" || [ $$? -eq 1 ]; } > "$$tmp"; mv "$$tmp" .env )

install:
	bun install --cwd $(FRONTEND)
	bun install --cwd $(SDK)

dev: keys
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui' 'validator@#fde047,$(RUN) validator' 'validator-ui@#5eead4,$(RUN) validator-ui'

dev-sandbox: keys
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway-sandbox' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui'

gateway-sandbox gateway-conformance: gateway
simulator-conformance: simulator

gateway: keys
	while IFS= read -r line || [ -n "$$line" ]; do case "$$line" in "#"*) ;; *=*) export "$$line" ;; esac; done < .env; \
		SPRING_PROFILES_ACTIVE=$(GATEWAY_PROFILES) $(GRADLE) :$@:infrastructure:bootRun --console=plain

simulator validator:
	SPRING_PROFILES_ACTIVE=$(PROFILES) $(GRADLE) :$@:infrastructure:bootRun --console=plain

console simulator-ui validator-ui:
	bun run --cwd $(FRONTEND)/apps/$(@:-ui=) dev

hash:
	@$(GRADLE) -q :gateway:infrastructure:bootJar
	@java -jar $(call JAR,gateway) hash

validate:
	@$(GRADLE) -q :validator:infrastructure:bootJar
	@java -jar $(call JAR,validator) validate --base-url $(TARGET) --webhook-port $(WEBHOOK_PORT) --report $(REPORT)

check: check-backend check-frontend check-sdk check-specs

check-backend:
	$(GRADLE) build

check-frontend:
	cd $(FRONTEND) && bunx turbo run build lint typecheck test && bunx prettier --check .

check-sdk:
	cd $(SDK) && bun run check && bun run test && bun run build

check-specs:
	REDOCLY_TELEMETRY=off bunx @redocly/cli@2.80.0 lint --config openapi/redocly.yaml openapi/openapi.yaml
	uvx sqlfluff@3.4.2 lint $(BACKEND)/*/infrastructure/src/main/resources/db/migration

demo: keys
	docker compose --profile demo up --build

clean:
	$(GRADLE) clean
	rm -rf $(FRONTEND)/apps/*/dist
