# Makefile to assist in running different container configurations for development and testing

# Detect container runtime.
CONTAINER_RUNTIME := $(shell command -v podman-compose 2>/dev/null || command -v "docker compose" 2>/dev/null)

.PHONY: dev-up minio-up test-up dev-down minio-down test-down all-down dev-logs minio-logs test-logs all-ps

dev-up:
	$(CONTAINER_RUNTIME) --project-name docview-dev -f docker-compose.yml up

minio-up:
	$(CONTAINER_RUNTIME) --project-name docview-minio -f docker-compose.minio.yml up

test-up:
	$(CONTAINER_RUNTIME) --project-name docview-test -f docker-compose.test.yml up

dev-down:
	$(CONTAINER_RUNTIME) --project-name docview-dev -f docker-compose.yml down

minio-down:
	$(CONTAINER_RUNTIME) --project-name docview-minio -f docker-compose.minio.yml down

test-down:
	$(CONTAINER_RUNTIME) --project-name docview-test -f docker-compose.test.yml down

minio-ps:
	$(CONTAINER_RUNTIME) --project-name docview-minio -f docker-compose.minio.yml ps

all-up: 
	$(CONTAINER_RUNTIME) --project-name docview-test -f docker-compose.test.yml up -d
	$(CONTAINER_RUNTIME) --project-name docview-minio -f docker-compose.minio.yml up -d
	$(CONTAINER_RUNTIME) --project-name docview-dev -f docker-compose.yml -f docker-compose.minio.yml up -d

all-down: dev-down minio-down test-down

all-ps:
	$(CONTAINER_RUNTIME) --project-name docview-test -f docker-compose.test.yml ps
	$(CONTAINER_RUNTIME) --project-name docview-minio -f docker-compose.minio.yml ps
	$(CONTAINER_RUNTIME) --project-name docview-dev -f docker-compose.yml ps

dev-logs:
	$(CONTAINER_RUNTIME) --project-name docview-dev logs -f

minio-logs:
	$(CONTAINER_RUNTIME) --project-name docview-minio -f docker-compose.minio.yml logs -f

test-logs:
	$(CONTAINER_RUNTIME) --project-name docview-test -f docker-compose.test.yml logs -f
