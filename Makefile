.DEFAULT_GOAL := help

.PHONY: help run up down logs status db infra build test test-integration check format clean

help:
	@printf '%s\n' \
		'make run               Build and start the backend, PostgreSQL and Kafka' \
		'make up                Same as make run' \
		'make down              Stop containers and keep database and Kafka data' \
		'make logs              Follow both service logs' \
		'make status            Show container status' \
		'make db                Start PostgreSQL only' \
		'make infra             Start PostgreSQL and Kafka' \
		'make build             Build both executable JARs' \
		'make test              Run unit and REST controller tests' \
		'make test-integration  Run distributed integration tests (requires Docker)' \
		'make check             Run KtLint and all tests (requires Docker)' \
		'make format            Apply KtLint formatting' \
		'make clean             Remove Gradle build output'

run:
	docker compose up --build -d --wait

up: run

down:
	docker compose down

logs:
	docker compose logs -f room-service booking-service

status:
	docker compose ps

db:
	docker compose up -d --wait db

infra:
	docker compose up -d --wait db kafka

build:
	./gradlew :room-service:bootJar :booking-service:bootJar

test:
	./gradlew test

test-integration:
	./gradlew :booking-service:integrationTest

check:
	./gradlew check :booking-service:integrationTest

format:
	./gradlew ktlintFormat

clean:
	./gradlew clean
