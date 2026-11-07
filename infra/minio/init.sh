#!/bin/sh
# MinIO 처음 설정: mall이 쓰는 버킷 `mall`을 만들고, 원본 mall 문서대로 누구나 읽게 둔다.
# [회사 설정] 버킷 공개 읽기 + 기본 루트 계정(minioadmin) — 6장·14장에서 고친다.
set -e
until mc alias set daon http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do sleep 1; done
mc mb --ignore-existing daon/mall
mc anonymous set download daon/mall
echo "minio-init: 버킷 mall 준비 끝"
