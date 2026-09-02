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
    && apt-get install -y --no-install-recommends git ca-certificates python3 python3-pip \
    && useradd --create-home --uid 10001 agent \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/bug-fixer-agent-0.1.0-SNAPSHOT.jar /app/bug-fixer-agent.jar
COPY runtime /app/runtime
RUN python3 -m pip install --no-cache-dir -r /app/runtime/openhands-requirements.txt \
    && python3 -m pip install --no-cache-dir --no-deps -r /app/runtime/openhands-tools-requirements.txt \
    && CUSTOM_TIKTOKEN_CACHE_DIR=/app/runtime/.tiktoken-cache python3 -c "import tiktoken; tiktoken.get_encoding('cl100k_base')" \
    && chown -R agent:agent /app
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/bug-fixer-agent.jar"]
