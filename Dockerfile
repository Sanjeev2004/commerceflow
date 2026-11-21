# Multi-stage build with POM caching to avoid redownloading Maven dependencies
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy root and all module POMs to leverage Docker layer caching
COPY pom.xml .
COPY common/pom.xml common/
COPY auth-service/pom.xml auth-service/
COPY product-service/pom.xml product-service/
COPY inventory-service/pom.xml inventory-service/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY search-service/pom.xml search-service/
COPY notification-service/pom.xml notification-service/
COPY gateway/pom.xml gateway/
COPY integration-tests/pom.xml integration-tests/
COPY cloud-service/pom.xml cloud-service/

# Pre-fetch dependencies (cached unless a pom.xml changes)
RUN mvn -B -ntp dependency:go-offline -DskipTests || true

# Copy all source files and build
COPY . .
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
 && addgroup -S app && adduser -S app -G app
WORKDIR /app
ARG SERVICE
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-1.0.0-SNAPSHOT-exec.jar app.jar
USER app
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 CMD wget -q -O /dev/null http://localhost:9090/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]
