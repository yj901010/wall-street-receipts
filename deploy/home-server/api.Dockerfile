# syntax=docker/dockerfile:1.7

ARG TEMURIN_JDK_IMAGE=eclipse-temurin:21.0.12_8-jdk-noble
ARG TEMURIN_JRE_IMAGE=eclipse-temurin:21.0.12_8-jre-noble

FROM ${TEMURIN_JDK_IMAGE} AS build
WORKDIR /workspace/apps/api
COPY apps/api/.mvn .mvn
COPY apps/api/mvnw apps/api/pom.xml ./
RUN chmod 0755 mvnw \
    && ./mvnw -B -ntp -DskipTests dependency:go-offline
COPY apps/api/src src
COPY fixtures/v1 /workspace/fixtures/v1
RUN ./mvnw -B -ntp -DskipTests package

FROM ${TEMURIN_JRE_IMAGE} AS runtime
ARG WSR_GIT_SHA=local
LABEL org.opencontainers.image.title="Wall Street Receipts API" \
      org.opencontainers.image.revision="${WSR_GIT_SHA}"
RUN command -v curl >/dev/null 2>&1 \
    && groupadd --gid 10001 wsr \
    && useradd --uid 10001 --gid 10001 --no-create-home \
        --home-dir /opt/wsr --shell /usr/sbin/nologin wsr
WORKDIR /opt/wsr
COPY --from=build --chown=10001:10001 \
    /workspace/apps/api/target/wall-street-receipts-api-0.0.1-SNAPSHOT.jar \
    /opt/wsr/application.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/wsr/application.jar"]
