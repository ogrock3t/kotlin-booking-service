.DEFAULT_GOAL := help

.PHONY: help init up down logs status db build test test-integration check clean

help:
	@printf '%s\n' \
		'make up                Build and start the app and PostgreSQL' \
		'make down              Stop containers and keep database data' \
		'make logs              Follow application logs' \
		'make status            Show container status' \
		'make db                Start PostgreSQL only' \
		'make build             Build the executable JAR' \
		'make test              Run unit tests' \
		'make test-integration  Run integration tests (requires Docker)' \
		'make check             Run all tests (requires Docker)' \
		'make clean             Remove Gradle build output'

init:
	@test -f .env || cp .env.example .env

up: init
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f app

status:
	docker compose ps

db: init
	docker compose up -d db

build:
	./gradlew bootJar

test:
	./gradlew test

test-integration:
	./gradlew integrationTest

check:
	./gradlew test integrationTest

clean:
	./gradlew clean
