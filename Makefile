.PHONY: help build dev up down clean demo health logs test

help:
	@echo "CommerceFlow Development Commands:"
	@echo "  make dev      Start lightweight development stack (1 Postgres, saves ~1.5GB RAM)"
	@echo "  make up       Start full production-grade stack (18 containers)"
	@echo "  make down     Stop running containers"
	@echo "  make clean    Stop containers and wipe volumes"
	@echo "  make build    Build with Maven"
	@echo "  make test     Run unit tests"
	@echo "  make health   Check service health status"
	@echo "  make logs     Tail application service logs"

build:
	mvn -B -ntp package -DskipTests

test:
	mvn -B -ntp verify -DskipITs

dev:
	docker compose -f docker-compose.dev.yml up -d --build --wait --wait-timeout 300
	@bash scripts/health-check.sh

up:
	docker compose up -d --build --wait --wait-timeout 300
	@bash scripts/health-check.sh

down:
	docker compose down
	docker compose -f docker-compose.dev.yml down

clean:
	docker compose down -v
	docker compose -f docker-compose.dev.yml down -v

health:
	@bash scripts/health-check.sh

logs:
	docker compose logs -f gateway order-service inventory-service payment-service
