FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q package

FROM node:22-bookworm AS node

FROM eclipse-temurin:21-jre-jammy
COPY --from=node /usr/local /usr/local
RUN apt-get update \
    && apt-get install -y --no-install-recommends git ca-certificates \
    && npm install -g opencode-ai@1.18.19 \
    && useradd --create-home --uid 10001 agent \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/bug-fixer-agent-0.1.0-SNAPSHOT.jar /app/bug-fixer-agent.jar
COPY runtime /app/runtime
RUN chown -R agent:agent /app
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/bug-fixer-agent.jar"]
