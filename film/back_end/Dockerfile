# syntax=docker/dockerfile:1

# ===== Build =====
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Tải dependency trước để Docker cache lại lớp này khi chỉ sửa code
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Pdocker dependency:go-offline
COPY src ./src
# Profile docker bật Tomcat nhúng để chạy bằng java -jar
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Pdocker -DskipTests package \
    && cp target/*.war /src/app.war

# ===== Run =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /src/app.war /app/app.war
USER app

ENV TZ=UTC \
    SERVER_PORT=8081 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081

# liveness không phụ thuộc Redis / RabbitMQ / MinIO
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://127.0.0.1:${SERVER_PORT}/actuator/health/liveness | grep -q UP || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.war"]
