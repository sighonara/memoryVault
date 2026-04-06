# Stage 1: Build the application
FROM eclipse-temurin:22-jdk-alpine AS builder
WORKDIR /app
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:22-jre-alpine
WORKDIR /app

# Create a non-root user
RUN addgroup -S memoryvault && adduser -S memoryvault -G memoryvault

COPY --from=builder /app/build/libs/*.jar app.jar

# Switch to non-root user
USER memoryvault

EXPOSE 8085

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
