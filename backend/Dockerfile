# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build
#
# Dependencies are resolved in their own layer, before the source is copied, so
# editing a Java file does not re-download the world. Tests are skipped here on
# purpose: CI runs them against a real PostgreSQL via Testcontainers, which is
# not available inside an image build.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# wget for the container health check; tzdata so timestamps are not all UTC-naive
RUN apk add --no-cache wget tzdata

# Never root. A container that is compromised through the application should
# not also own the filesystem it runs on.
RUN addgroup -S karvya && adduser -S -G karvya karvya

WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

# The media volume is mounted here; created up front with the right owner so
# the unprivileged user can write uploads into it.
RUN mkdir -p /var/lib/karvya/media && chown -R karvya:karvya /var/lib/karvya /app

USER karvya

EXPOSE 8080

# Container-aware defaults: the JVM reads the cgroup memory limit rather than
# the host's, so a memory cap on the container is actually respected.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
