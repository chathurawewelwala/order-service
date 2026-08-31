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

# eclipse-temurin already has UID/GID 1000 (ubuntu). Reuse them so this
# matches Deployment securityContext runAsUser/fsGroup: 1000.
# Do not groupadd/useradd 1000 — that fails with "GID already exists".
RUN mkdir -p /app/dumps /app/logs \
    && chown -R 1000:1000 /app/dumps /app/logs

USER 1000:1000

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
