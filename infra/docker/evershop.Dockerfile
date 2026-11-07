# syntax=docker/dockerfile:1
# 다온마켓 Express 스택(EverShop 포크, GPL-3.0). 빌드 컨텍스트는 express/ 이다.
# 원본 express/Dockerfile(개발용, 소스에서 빌드)과 같은 순서이고, 기반 이미지만 고정 버전으로 바꿨다.
FROM node:20.19.5-alpine
WORKDIR /app
ENV HUSKY=0
COPY . .
RUN mkdir -p config themes extensions public media
RUN --mount=type=cache,target=/root/.npm \
    npm install --no-audit --no-fund \
 && npm run compile \
 && npm run compile:db \
 && npm run build
ENV NODE_ENV=production TZ=Asia/Seoul
EXPOSE 3000
CMD ["npm", "run", "start"]
