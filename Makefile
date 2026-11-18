BACKEND := backend
FRONTEND := frontend
GRADLE := ./$(BACKEND)/gradlew -p $(BACKEND)
MULTIPLEX := $(FRONTEND)/node_modules/.bin/multiplex --title OpenFSP
RUN := make --no-print-directory
VALIDATOR := $(BACKEND)/validator/infrastructure/build
VALIDATE := java -jar $(VALIDATOR)/libs/app.jar validate --conformance conformance --assisted-ok

export OPENFSP_ADMIN_TOKEN ?= $$argon2id$$v=19$$m=65536,t=3,p=4$$PEua5g9jsR2Ot2g7bhkBWT4vxejdDAl8jlbiptXsNzI$$ZlQNIj02jEudsOtGDc/XRMcHIW2jOfSAzq/68WI+x5c
export OPENFSP_BOOTSTRAP_KEY ?= ofsp_test_DemoShopKeyPublishedForTheSimulator01

TARGET ?= http://127.0.0.1:8080
REPORT ?= $(VALIDATOR)/conformance-report.json
WEBHOOK_PORT ?= 8099

.DEFAULT_GOAL := help
.PHONY: help install dev dev-sandbox gateway gateway-sandbox gateway-conformance simulator simulator-conformance console simulator-ui validator validator-ui hash validate check demo clean

help:
	@grep -E '^[a-z -]+:' $(MAKEFILE_LIST) | cut -d: -f1 | grep -v '^help$$' | tr '\n' ' '; echo

install:
	bun install --cwd $(FRONTEND)

dev:
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui' 'validator@#fde047,$(RUN) validator' 'validator-ui@#5eead4,$(RUN) validator-ui'

dev-sandbox:
	$(MULTIPLEX) 'gateway@#93c5fd,$(RUN) gateway-sandbox' 'simulator@#c4b5fd,$(RUN) simulator' 'console@#fb7185,$(RUN) console' \
		'simulator-ui@#fdba74,$(RUN) simulator-ui'

gateway simulator validator:
	$(GRADLE) :$@:infrastructure:bootRun --console=plain

gateway-sandbox:
	set -a; [ ! -f .env.sandbox ] || . ./.env.sandbox; set +a; SPRING_PROFILES_ACTIVE=development,sandbox $(RUN) gateway

gateway-conformance:
	SPRING_PROFILES_ACTIVE=development,conformance $(RUN) gateway

simulator-conformance:
	SPRING_PROFILES_ACTIVE=conformance $(RUN) simulator

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
	REDOCLY_TELEMETRY=off bunx @redocly/cli@2.72.1 lint --config openapi/redocly.yaml openapi/openapi.yaml

demo:
	docker compose --profile demo up --build

clean:
	$(GRADLE) clean
	rm -rf $(FRONTEND)/apps/*/dist
