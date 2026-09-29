FROM node:22-alpine AS build
WORKDIR /web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build
FROM caddy:2.10-alpine
COPY --from=build /web/dist/web/browser /srv/web
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
COPY infra/Caddyfile.dev /etc/caddy/Caddyfile.dev
RUN caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile \
    && caddy validate --config /etc/caddy/Caddyfile.dev --adapter caddyfile
