FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src/ src/
RUN mvn --batch-mode --no-transfer-progress clean verify

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S voltforge && adduser -S voltforge -G voltforge
COPY --from=builder /app/target/*.jar app.jar
RUN chown voltforge:voltforge app.jar
USER voltforge
EXPOSE 2001
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:2001/voltForge-app/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
