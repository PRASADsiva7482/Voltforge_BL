FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:resolve
COPY src/ src/
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S voltforge && adduser -S voltforge -G voltforge
COPY --from=builder /app/target/*.jar app.jar
RUN chown voltforge:voltforge app.jar
USER voltforge
EXPOSE 2001
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost:2001/voltForge-app/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
