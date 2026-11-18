FROM scratch AS conformance

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build

WORKDIR /src/backend

COPY . .
COPY --from=conformance . /src/conformance

ARG MODULE

RUN --mount=type=cache,target=/root/.gradle ./gradlew ":${MODULE}:bootJar" --no-daemon --console=plain && cp "$(echo "${MODULE}" | tr ':' '/')/build/libs/app.jar" /app.jar


FROM eclipse-temurin:25-jre

ARG PORT

ENV HEALTH_URL=http://127.0.0.1:${PORT}/actuator/health

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 openfsp

COPY --from=build /app.jar /opt/openfsp/app.jar

USER 10001

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=5 CMD ["sh", "-c", "curl -fsS -o /dev/null \"$HEALTH_URL\""]

ENTRYPOINT ["java", "-jar", "/opt/openfsp/app.jar"]
