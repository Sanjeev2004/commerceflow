# Single process for a host that runs one web service plus managed Postgres and Redis.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
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
RUN mvn -B -ntp -pl cloud-service -am dependency:go-offline -DskipTests || true
COPY . .
RUN mvn -B -ntp -pl cloud-service -am package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
 && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/cloud-service/target/cloud-service-1.0.0-SNAPSHOT.jar app.jar
USER app
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=12 CMD wget -q -O /dev/null http://127.0.0.1:${PORT:-8080}/health || exit 1
ENTRYPOINT ["java", "-Xmx160m", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=32m", "-Xss256k", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
