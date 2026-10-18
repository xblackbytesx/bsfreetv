# BSfreeTV. Everything runs in Docker; no host toolchain needed.
#
#   make test         JVM unit tests
#   make build        debug APK -> build-output/bsfreetv-debug.apk
#   make release      release APK (signed when keystore.properties / env vars exist)
#   make lint         Android lint
#   make screenshots  render the screens at TV size -> build-output/screenshots/
#   make keystore     create the release signing key (once)
#   make install      adb install the debug APK on $(SHIELD) (adb connect first)
#   make baseline-profile  record a Baseline Profile on $(SHIELD); commit
#                     app/src/main/generated/baselineProfiles/ afterwards

COMPOSE = docker compose -f docker/docker-compose.yml
SHIELD ?= shield:5555

.PHONY: test build release lint screenshots keystore install baseline-profile

test:
	$(COMPOSE) run --rm --build test

build:
	$(COMPOSE) run --rm --build build

release:
	$(COMPOSE) run --rm --build build-release

lint:
	$(COMPOSE) run --rm --build lint

screenshots:
	$(COMPOSE) run --rm --build screenshots

keystore:
	$(COMPOSE) run --rm --build keystore

baseline-profile:
	SHIELD=$(SHIELD) $(COMPOSE) run --rm --build baseline-profile

install:
	adb -s $(SHIELD) install -r build-output/bsfreetv-debug.apk
