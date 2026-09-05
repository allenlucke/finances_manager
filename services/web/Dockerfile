# syntax=docker/dockerfile:1
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
# `npm ci` and nothing else. It used to fall back to `npm install`, which resolves fresh from
# version ranges whenever the lockfile drifts — shipping dependencies nobody pinned, silently.
# A failing `npm ci` is the lockfile telling you something.
RUN npm ci
COPY . .
RUN npm run build

# The unprivileged variant: nginx runs as a non-root user and listens on 8080. Compose maps the
# published port accordingly.
FROM nginxinc/nginx-unprivileged:alpine
COPY --from=build /build/dist/web/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
