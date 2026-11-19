FROM oven/bun:1.4.4 AS build
WORKDIR /src
COPY . .
RUN bun install --frozen-lockfile && bunx turbo build

FROM nginxinc/nginx-unprivileged:1.31-alpine AS console
ENV SENTRY_DSN="" SENTRY_ENVIRONMENT="" SENTRY_RELEASE="" SENTRY_TRACES_SAMPLE_RATE=""
COPY apps/console/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/console/dist /usr/share/nginx/html
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 CMD ["curl", "-fsS", "-o", "/dev/null", "http://127.0.0.1:8080/health"]

FROM nginxinc/nginx-unprivileged:1.31-alpine AS simulator
ENV SIMULATOR_CONTROL_KEY="" SENTRY_DSN="" SENTRY_ENVIRONMENT="" SENTRY_RELEASE="" SENTRY_TRACES_SAMPLE_RATE=""
COPY apps/simulator/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/simulator/dist /usr/share/nginx/html
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 CMD ["curl", "-fsS", "-o", "/dev/null", "http://127.0.0.1:8080/health"]

FROM nginxinc/nginx-unprivileged:1.31-alpine AS validator
ENV SENTRY_DSN="" SENTRY_ENVIRONMENT="" SENTRY_RELEASE="" SENTRY_TRACES_SAMPLE_RATE=""
COPY apps/validator/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/validator/dist /usr/share/nginx/html
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 CMD ["curl", "-fsS", "-o", "/dev/null", "http://127.0.0.1:8080/health"]

