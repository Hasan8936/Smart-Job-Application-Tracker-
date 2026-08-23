FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
COPY src ./src
COPY index.html ./
COPY vite.config.mjs ./
COPY tailwind.config.cjs ./
COPY postcss.config.cjs ./
# Vite bakes VITE_* vars into the bundle at build time — a runtime `environment:`
# entry in docker-compose has no effect on an already-built image, so this must
# be passed in as a build ARG instead (see docker-compose.yml's `build.args`).
ARG VITE_API_BASE=http://localhost:8080/api
ENV VITE_API_BASE=$VITE_API_BASE
# Cypress is a devDependency used only for local/CI e2e tests; skip its binary
# download during the image build so it doesn't fail in network-restricted
# build environments (and to keep the build fast).
ENV CYPRESS_INSTALL_BINARY=0
RUN npm ci && npm run build

FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# Keep client-side routes working when users refresh or deep-link into the app.
RUN printf 'server {\n  listen 80;\n  server_name _;\n  root /usr/share/nginx/html;\n  location / {\n    try_files $uri $uri/ /index.html;\n  }\n}\n' > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
