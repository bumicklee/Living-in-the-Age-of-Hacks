# syntax=docker/dockerfile:1
# 다온마켓 A8(7-5) · CI 러너와 배포 확인에 쓰는 도구 이미지. 교육용 랩 전용.
#   러너: Gitea Actions 러너(act_runner)를 「호스트 모드」로 돌린다. 도커 소켓을 받지 않는다(7-5 러너 최소 권한).
#   도구: git·python3·skopeo(이미지 옮기기)·cosign(서명·확인, 인터넷 없이 키 파일로만 쓴다).
FROM gitea/act_runner:0.6.1
RUN apk add --no-cache python3 git curl skopeo ca-certificates apache2-utils \
 && adduser -D -u 10002 runner && mkdir -p /data /ci-in && chown runner /data
COPY --from=ghcr.io/sigstore/cosign/cosign:v2.5.3 /ko-app/cosign /usr/local/bin/cosign
USER runner
WORKDIR /data
