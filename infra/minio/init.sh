#!/bin/sh
# MinIO 처음 설정: mall이 쓰는 버킷 `mall`을 만들고, 원본 mall 문서대로 누구나 읽게 둔다.
# [회사 설정] 버킷 공개 읽기 + 기본 루트 계정(minioadmin) — 6장·14장에서 고친다.
# ✏️ 6-4: 루트 계정은 이 서버가 처음부터 다른 값으로 뜬다(compose가 MINIO_ROOT_*를 .env의 값으로 넘김). 이 스크립트는 그 값으로 들어와 앱 전용 계정을 맞춘다.
set -e
until mc alias set daon http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do sleep 1; done
mc mb --ignore-existing daon/mall
# 다온마켓 3-6: 목록은 닫고 객체 읽기만 연다(상품 사진은 공개 주소로 보여야 한다). 관리 계정·포트는 6-4에서 고친다
cat > /tmp/mall-read-only.json <<'JSON'
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},"Action":["s3:GetObject"],"Resource":["arn:aws:s3:::mall/*"]}]}
JSON
mc anonymous set-json /tmp/mall-read-only.json daon/mall
# ✏️ 6-4: 앱 전용 접근 키. 버킷 mall 하나에 대한 목록·읽기·쓰기·삭제만(버킷 만들기·정책 바꾸기·다른 버킷 없음). 멱등: 이미 있으면 비밀 키만 맞춘다
if [ -n "${DAON_MINIO_APP_ACCESS_KEY:-}" ]; then
  cat > /tmp/daon-app-policy.json <<'JSON'
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:ListBucket","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::mall"]},
 {"Effect":"Allow","Action":["s3:GetObject","s3:PutObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::mall/*"]}]}
JSON
  mc admin policy create daon daon-app /tmp/daon-app-policy.json >/dev/null
  mc admin user add daon "$DAON_MINIO_APP_ACCESS_KEY" "$DAON_MINIO_APP_SECRET_KEY" >/dev/null
  mc admin policy attach daon daon-app --user "$DAON_MINIO_APP_ACCESS_KEY" >/dev/null 2>&1 || true   # 이미 붙어 있으면 오류라서 넘어간다(시험이 앱 키로 올리기를 확인)
  echo "minio-init: 앱 전용 접근 키 준비(버킷 mall만)"
fi
echo "minio-init: 버킷 mall 준비 끝"
