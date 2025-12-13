# Build stage
FROM --platform=$BUILDPLATFORM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy gradle files first for caching
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Download dependencies
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build application
RUN gradle bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Set timezone
ENV TZ=Asia/Seoul

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
