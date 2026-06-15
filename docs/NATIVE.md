# GraalVM Native Image

OneLine can be compiled to a standalone native executable via GraalVM for fast startup and a much smaller memory footprint.

## Build details and options

Spring Boot AOT processing only reads bean definitions. The native build needs no running Postgres, Redis, MinIO.
They are only required when the finished binary runs.

### Self-contained Docker build (recommended)

No local GraalVM needed. Build for native image can be executed with the next command:
```bash
docker build -f docker/Dockerfile.native -t oneline-native .
```

### Validate the AOT pipeline without GraalVM

Runs on any JDK, no services required - good for CI smoke-checks:
```bash
./gradlew processAot
```

## Runtime

The native binary takes the same env vars as the JVM image and the same `ONELINE_*` property overrides.
Run the following to start Postgres, Redis, MinIO and OneLine after successful native build:
```bash
docker compose -f docker/docker-compose.yml -f docker/docker-compose.native.yml up
```