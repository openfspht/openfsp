FROM oven/bun:1.4.2 AS build
WORKDIR /src
COPY . .
RUN bun install --frozen-lockfile && bunx turbo build

FROM nginxinc/nginx-unprivileged:1.29-alpine AS console
COPY apps/web/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/web/dist /usr/share/nginx/html

FROM nginxinc/nginx-unprivileged:1.29-alpine AS simulator
COPY apps/simulator/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/simulator/dist /usr/share/nginx/html

FROM oven/bun:1.4.2-slim AS shop
WORKDIR /src
COPY --from=build /src /src
USER bun
WORKDIR /src/apps/shop
ENV HOST=0.0.0.0
ENTRYPOINT ["bun", "server/main.ts"]
