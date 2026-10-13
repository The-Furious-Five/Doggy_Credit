FROM --platform=$BUILDPLATFORM node:22-alpine AS build
WORKDIR /web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build
FROM caddy:2.10-alpine
COPY --from=build /web/dist/web/browser /srv/web
# E-13: el commit de la release se publica en /release.json para comprobar desde
# Internet qué versión está en línea. El CI pasa el SHA; en local queda "local".
ARG DC_RELEASE=local
RUN printf '{"release":"%s"}\n' "$DC_RELEASE" > /srv/web/release.json
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
RUN caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
