# Build stage: Vite 8 requires Node >= 20.19 / >= 22.12, so pin Node 22.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
# Vite bakes VITE_* vars into the bundle at build time, so pass the API base as a
# build argument rather than a runtime environment value.
ARG VITE_API_BASE=http://localhost:8080/api
ENV VITE_API_BASE=$VITE_API_BASE
ENV CYPRESS_INSTALL_BINARY=0
RUN npm ci && npm run build
COPY index.html vite.config.js postcss.config.cjs tailwind.config.cjs ./
COPY src ./src

# Serve the static build with nginx.
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html

# SPA fallback: send unknown paths to index.html so client-side routes
# (/login, /register, /profile) don't 404 on refresh or deep-link.
RUN printf 'server {\n  listen 80;\n  server_name _;\n  root /usr/share/nginx/html;\n  location / {\n    try_files $uri $uri/ /index.html;\n  }\n}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
