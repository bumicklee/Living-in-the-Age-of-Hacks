# syntax=docker/dockerfile:1
# 관리자 화면(mall-admin-web 포크, Vue 3 + Vite). 빌드 컨텍스트는 spring/mall-admin-web/ 이다.
# API 주소는 빌드할 때 정해진다(Vite가 VITE_ 환경 변수를 .env 파일보다 먼저 읽는다).
FROM node:22.21.1-alpine AS build
WORKDIR /web
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
ARG VITE_BASE_SERVER_URL=http://127.0.0.1:8080
ENV VITE_BASE_SERVER_URL=${VITE_BASE_SERVER_URL}
# type-check(vue-tsc)는 건너뛰고 번들만 만든다
RUN npx vite build --mode production

FROM nginx:1.28.1-alpine
COPY --from=build /web/dist /usr/share/nginx/html
EXPOSE 80
