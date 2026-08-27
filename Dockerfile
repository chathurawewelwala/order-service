# ========== Stage 1: Build ==========
FROM eclipse-temurin:26-jdk AS builder

WORKDIR /app

# Copy Maven wrapper & pom first (better layer caching)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster build)
RUN ./mvnw clean package -DskipTests

# ========== Stage 2: Runtime ==========
FROM eclipse-temurin:26-jre

WORKDIR /app

# Add a non-root user (security best practice)
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# JVM options optimized for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]