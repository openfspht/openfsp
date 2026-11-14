FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /src/backend
COPY . .
COPY --from=conformance . /src/conformance
RUN --mount=type=cache,target=/root/.gradle ./gradlew :validator-web:bootJar --no-daemon --console=plain && cp validator-web/build/libs/app.jar /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 openfsp
COPY --from=build /app.jar /opt/openfsp/app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "/opt/openfsp/app.jar"]
