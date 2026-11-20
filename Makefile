# MediaManager for Android — build from the repo root.
# Requires JDK 21 and the Android SDK (ANDROID_HOME or local.properties).

SHELL := /bin/bash
APK := androidApp/build/outputs/apk/debug/app-arm64-v8a-debug.apk
RELEASE_APK := androidApp/build/outputs/apk/release/app-arm64-v8a-release.apk

.PHONY: help build test lint coverage check install clean release

help:
	@echo "make build    assemble the debug APK"
	@echo "make test     unit + Robolectric tests"
	@echo "make lint     Android lint"
	@echo "make coverage unit-test coverage report (HTML under androidApp/build/reports/coverage)"
	@echo "make check    test + lint"
	@echo "make install  install on the connected phone if it fits"
	@echo "make release  signed release APK (needs the keystore)"

build:
	./gradlew assembleDebug

test:
	./gradlew testDebugUnitTest

lint:
	./gradlew lint

coverage:
	./gradlew coverageReport

check: test lint

# Refuses to install when free space is below the APK size plus headroom.
install: build
	scripts/install-apk.sh $(APK)

release:
	@test -n "$$MM_STORE_FILE" || { echo "source the keystore properties first"; exit 1; }
	./gradlew assembleRelease \
		-PMM_STORE_FILE="$$MM_STORE_FILE" \
		-PMM_STORE_PASSWORD="$$MM_STORE_PASSWORD" \
		-PMM_KEY_ALIAS="$$MM_KEY_ALIAS" \
		-PMM_KEY_PASSWORD="$$MM_KEY_PASSWORD"
	@ls -lh $(RELEASE_APK)

clean:
	./gradlew clean
	rm -rf androidApp/build build .gradle .kotlin
