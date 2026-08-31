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

# JVM options: G1 + heap dump on OOM + GC log for the RCA lab.
# exec below makes `java` PID 1 so `jcmd 1 ...` works after kubectl exec.
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/dumps/heapdump.hprof \
               -XX:+ExitOnOutOfMemoryError \
               -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=20M"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
