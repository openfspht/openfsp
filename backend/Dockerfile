FROM scratch AS conformance

FROM scratch AS sdk

FROM scratch AS frontend

FROM --platform=$BUILDPLATFORM oven/bun:1.4.4 AS ui

WORKDIR /src

COPY --from=frontend . .

ARG UI

RUN test -n "${UI}" && bun install --frozen-lockfile && bunx turbo build --filter="@workspace/${UI}" && bun scripts/precompress.ts "apps/${UI}/dist" && mv "apps/${UI}/dist" /ui


FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build

WORKDIR /src/backend

COPY . .
COPY --from=conformance . /src/conformance
COPY --from=sdk . /src/sdks/kotlin
COPY --from=ui /ui /src/ui

ARG MODULE

RUN --mount=type=cache,target=/root/.gradle ./gradlew ":${MODULE}:bootJar" -PuiBundle=/src/ui --no-daemon --console=plain && cp "$(echo "${MODULE}" | tr ':' '/')/build/libs/app.jar" /app.jar


FROM eclipse-temurin:25-jre

ARG PORT

ENV HEALTH_URL=http://127.0.0.1:${PORT}/actuator/health

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 openfsp

COPY --from=build /app.jar /opt/openfsp/app.jar

USER 10001

HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=5 CMD ["sh", "-c", "curl -fsS -o /dev/null \"$HEALTH_URL\""]

ENTRYPOINT ["java", "-jar", "/opt/openfsp/app.jar"]
