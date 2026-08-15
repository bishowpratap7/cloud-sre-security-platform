# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml /workspace/
RUN mvn -q -B dependency:go-offline
COPY src /workspace/src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app

ARG OTEL_AGENT_VERSION=2.9.0
RUN wget -q -O /opentelemetry-javaagent.jar \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    && chmod 644 /opentelemetry-javaagent.jar

WORKDIR /app
COPY --from=build /workspace/target/incident-engine.jar /app/incident-engine.jar

USER app
EXPOSE 8080

ENV SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-javaagent:/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75" \
    SERVER_SHUTDOWN=graceful \
    SPRING_LIFECYCLE_TIMEOUT-PER-SHUTDOWN-PHASE=20s

ENTRYPOINT ["java", "-jar", "/app/incident-engine.jar"]
