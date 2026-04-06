# Stage 1: Build the frontend
FROM node:20-alpine AS frontend
WORKDIR /app/client
COPY client/package.json client/package-lock.json ./
RUN npm ci
COPY client/ ./
RUN npm run build

# Stage 2: Build the backend
FROM eclipse-temurin:22-jdk-alpine AS builder
WORKDIR /app
COPY gradlew ./
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY src/ src/
# Copy frontend build into Spring Boot static resources
COPY --from=frontend /app/client/dist/client/browser/ src/main/resources/static/
RUN ./gradlew bootJar --no-daemon

# Stage 3: Run the application
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
