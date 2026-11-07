# syntax=docker/dockerfile:1

# ===== Build =====
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ===== Serve =====
# nginx phục vụ file tĩnh, chuyển /api sang backend và /media sang MinIO: cả site chạy trên một domain
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
