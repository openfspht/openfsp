FROM oven/bun:1.4.4 AS build
WORKDIR /src
COPY . .
RUN bun install --frozen-lockfile && bunx turbo build

FROM nginxinc/nginx-unprivileged:1.31-alpine AS console
COPY apps/console/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/console/dist /usr/share/nginx/html

FROM nginxinc/nginx-unprivileged:1.31-alpine AS simulator
COPY apps/simulator/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/simulator/dist /usr/share/nginx/html

FROM nginxinc/nginx-unprivileged:1.31-alpine AS validator
COPY apps/validator/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/apps/validator/dist /usr/share/nginx/html

