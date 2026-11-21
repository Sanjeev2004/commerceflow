FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY target/*-exec.jar app.jar
USER app
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
