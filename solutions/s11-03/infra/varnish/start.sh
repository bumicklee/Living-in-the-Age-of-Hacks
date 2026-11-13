#!/bin/sh
# 다온마켓 A7 · Varnish 시작(11-3·11-4). VCL 파일만 고르고 이미지의 원래 시작 스크립트로 넘긴다.
#   방어 스위치 private-cache(11-4)가 꺼져 있고 옛 설정 사본(switch-off.vcl, 11-4 정답에만 있음)이 있으면 그것으로 뜬다.
#   스위치 서명(7-미션)은 앱만 확인한다. 이 스크립트는 값만 읽는다(랩 도구).
set -e
VARNISH_VCL_FILE=/etc/daon-varnish/default.vcl
if [ "${SECUREKIT_PRIVATE_CACHE_ENABLED:-true}" = "false" ] && [ -f /etc/daon-varnish/switch-off.vcl ]; then
  VARNISH_VCL_FILE=/etc/daon-varnish/switch-off.vcl
  echo "[랩 스위치] private-cache 꺼짐: 11-4 시작 지점의 회사 설정 VCL로 뜹니다"
fi
export VARNISH_VCL_FILE
exec /usr/local/bin/docker-varnish-entrypoint
