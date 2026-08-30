# ========== Stage 1: Build ==========
FROM eclipse-temurin:26-jdk AS builder

WORKDIR /app

# Copy Maven wrapper & pom
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Fix permission inside Docker
RUN chmod +x mvnw

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build
RUN ./mvnw clean package -DskipTests

# ========== Stage 2: Runtime ==========
FROM eclipse-temurin:26-jdk

WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring

# Create directory for heap dumps
RUN mkdir -p /app/dumps && chown spring:spring /app/dumps

USER spring:spring

COPY --from=builder /app/target/*.jar app.jar

# JVM options with HeapDump on OOM
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/dumps/heapdump.hprof \
               -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]