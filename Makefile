# MediaManager — run from the repo root.
# cargo runs with --manifest-path so the CWD stays at the repo root and
# dotenvy picks up the root .env.

SHELL := /bin/bash
API_DIR := crates/api
WEB_DIR := apps/web
CARGO_RUN := cargo run --manifest-path $(API_DIR)/Cargo.toml
ENGINE ?= podman

.PHONY: help install dev api worker web build test check lint fmt clean images

help:
	@echo "make install   install web deps + fetch rust deps"
	@echo "make dev       run API (:8787) and web (:3000) together"
	@echo "make api       run API only"
	@echo "make worker    run worker only (MM_ROLE=worker)"
	@echo "make web       run Next dev server"
	@echo "make test      cargo test + vitest"
	@echo "make test-web  vitest only"
	@echo "make e2e       browser smoke test (needs make dev running)"
	@echo "make check     cargo check + tsc --noEmit"
	@echo "make lint      eslint"
	@echo "make images    build api + web images with $(ENGINE)"

install:
	cd $(WEB_DIR) && npm install
	cargo fetch --manifest-path $(API_DIR)/Cargo.toml

dev:
	@trap 'kill 0' INT TERM; \
	$(MAKE) api & \
	$(MAKE) web & \
	wait

api:
	$(CARGO_RUN)

worker:
	MM_ROLE=worker $(CARGO_RUN)

web:
	cd $(WEB_DIR) && npm run dev

build:
	cargo build --release --manifest-path $(API_DIR)/Cargo.toml
	cd $(WEB_DIR) && npm run build

test:
	cargo test --manifest-path $(API_DIR)/Cargo.toml
	cd $(WEB_DIR) && npm test

test-web:
	cd $(WEB_DIR) && npm test

e2e:
	cd $(WEB_DIR) && npm run e2e

check:
	cargo check --manifest-path $(API_DIR)/Cargo.toml
	cd $(WEB_DIR) && npx tsc --noEmit

lint:
	cd $(WEB_DIR) && npm run lint

fmt:
	cargo fmt --manifest-path $(API_DIR)/Cargo.toml

clean:
	cargo clean --manifest-path $(API_DIR)/Cargo.toml
	rm -rf $(WEB_DIR)/.next

images:
	$(ENGINE) build -f $(API_DIR)/Dockerfile -t localhost/mm-api:local .
	$(ENGINE) build -f $(WEB_DIR)/Dockerfile \
		--build-arg MM_API_URL=http://api:8787 \
		-t localhost/mm-web:local $(WEB_DIR)
