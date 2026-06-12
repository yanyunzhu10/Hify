FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY hify-common/pom.xml hify-common/pom.xml
COPY hify-provider/pom.xml hify-provider/pom.xml
COPY hify-mcp/pom.xml hify-mcp/pom.xml
COPY hify-agent/pom.xml hify-agent/pom.xml
COPY hify-knowledge/pom.xml hify-knowledge/pom.xml
COPY hify-workflow/pom.xml hify-workflow/pom.xml
COPY hify-chat/pom.xml hify-chat/pom.xml
COPY hify-app/pom.xml hify-app/pom.xml

RUN mvn -pl hify-app -am dependency:go-offline -DskipTests

COPY hify-common/src hify-common/src
COPY hify-provider/src hify-provider/src
COPY hify-mcp/src hify-mcp/src
COPY hify-agent/src hify-agent/src
COPY hify-knowledge/src hify-knowledge/src
COPY hify-workflow/src hify-workflow/src
COPY hify-chat/src hify-chat/src
COPY hify-app/src hify-app/src

RUN mvn -pl hify-app -am clean package -DskipTests && \
    cp hify-app/target/hify-app-*.jar /workspace/app.jar

FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl && \
    addgroup -S hify && \
    adduser -S -G hify -h /app hify

WORKDIR /app

COPY --from=build --chown=hify:hify /workspace/app.jar /app/app.jar

RUN mkdir -p /app/logs /app/upload && \
    chown -R hify:hify /app

USER hify

EXPOSE 8080

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/v1/health" >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar --server.port=${SERVER_PORT} --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
