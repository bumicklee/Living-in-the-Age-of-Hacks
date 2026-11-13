"""방어 스위치(A4)와 랩 상황판(A8). 랩-설계.md 12·13절.

방어 스위치
  make switch                       목록과 지금 값
  make switch K=ownership V=off     끄기 → .daon/switches.env → 그 앱만 다시 띄움(30초쯤)
  make switch K=all V=on            전부 켜기(기본값으로 돌아가기)
  - 스위치를 읽는 코드는 수업 정답(완료 지점)에만 있다. 시작 지점에서는 바꿔도 아무 일도 없다(손으로 고치는 실습은 그대로).
  - Spring은 secure-kit.<이름>.enabled 를 환경 변수 SECUREKIT_<이름>_ENABLED 로 받는다(SecureKitSwitch).

랩 상황판
  make lab                          http://127.0.0.1:8099/lab (읽기 전용, 이 PC에서만)
  python -m daon lab --json         같은 내용을 JSON 한 번
"""
from __future__ import annotations

import json
import time
from datetime import datetime
from pathlib import Path

from daon import course, stack as st
from daon.config import DATA, ENV, ROOT, current_stack

SIG_NAME = "SECUREKIT_SIG"
SIGN_KEY_NAME = "DAON_SWITCH_SIGN_KEY"
SIGN_KEY_DEFAULT = "lab-fake-switch-sign-0001"       # 랩 전용 가짜 값(infra/.env.example의 값과 같다)


def sign(off_lines: list[str]) -> str:
    """꺼진 스위치 줄(NAME=false)을 정렬해 이은 글자의 HMAC-SHA256(16진수). 앱의 SecureKitSwitch가 같은 방식으로 확인한다"""
    import hashlib
    import hmac
    key = ENV.get(SIGN_KEY_NAME, SIGN_KEY_DEFAULT).encode()
    return hmac.new(key, "\n".join(sorted(off_lines)).encode(), hashlib.sha256).hexdigest()


# 이름 → 수업·앱·재연. SecureKitSwitch.KNOWN(Java)과 같은 이름을 쓴다
SWITCHES = {
    "ownership": {"lesson": "s2-02", "service": "mall-portal", "replay": "T01",
                  "title": "주문 소유권 검사(2-2·2-미션)"},
    "admin-default-deny": {"lesson": "s2-03", "service": "mall-admin", "replay": "T02",
                           "title": "관리자 기본 거부(2-3)"},
    "request-dto": {"lesson": "s2-04", "service": "mall-portal", "replay": "T03",
                    "title": "회원정보 요청 전용 DTO(2-4)"},
    "egress": {"lesson": "s2-05", "service": "mall-admin", "replay": "T04",
               "title": "나가는 요청 검사(2-5)"},
    "host-allowlist": {"lesson": "s2-06", "service": "mall-admin", "replay": "T05",
                       "title": "운영 도구 Host 허용 목록(2-6)"},
    "query-binding": {"lesson": "s3-02", "service": "mall-portal", "replay": "T06",
                      "title": "검색어 바인딩(3-2)"},
    "sort-allowlist": {"lesson": "s3-03", "service": "mall-portal", "replay": "T06",
                       "title": "정렬 허용 목록(3-3)"},
    "html-sanitize": {"lesson": "s3-04", "service": "mall-admin", "replay": "T07",
                      "title": "후기 글자 출력(3-4)"},
    "headers": {"lesson": "s3-05", "service": "admin-web", "replay": "T07",
                "title": "관리자 화면 CSP 헤더(3-5, html-sanitize와 함께 끄면 레드)"},
    "upload": {"lesson": "s3-06", "service": "mall-admin", "replay": "T08",
               "title": "업로드 검사: 크기·내용·이름(3-6)"},
    # 섹션 4. service가 둘이면 둘 다 다시 띄운다(관리자 로그인에도 같은 보호가 붙는다)
    "password-policy": {"lesson": "s4-02", "service": "mall-portal", "replay": "T09",
                        "title": "비밀번호 길이·유출 목록 거부(4-2)"},
    "login-ratelimit": {"lesson": "s4-03", "service": ["mall-portal", "mall-admin"], "replay": "T09",
                        "title": "로그인 속도 제한: 계정·IP·기기(4-3)"},
    "login-timing": {"lesson": "s4-04", "service": ["mall-portal", "mall-admin"], "replay": "T10",
                     "title": "없는 계정도 더미 해시 비교·문구 하나(4-4)"},
    "reset-flow": {"lesson": "s4-05", "service": "mall-portal", "replay": "T11",
                   "title": "재설정 링크는 설정 주소로·인증 번호는 문자로만(4-5)"},
    "token-revoke": {"lesson": "s4-08", "service": ["mall-portal", "mall-admin"], "replay": "T13",
                     "title": "로그아웃 폐기 목록·갱신 토큰 회전(4-8)"},
    "step-up": {"lesson": "s4-07", "service": ["mall-portal", "mall-admin"], "replay": "T12",
                "title": "고위험 작업 재인증·관리자 비밀번호 변경 분리(4-7)"},
    "pii-encrypt": {"lesson": "s5-03", "service": ["mall-portal", "mall-admin"], "replay": "T14",
                    "title": "개인정보 칸 봉투 암호화·캐시 가림(5-3)"},
    "jwt-hardening": {"lesson": "s5-02", "service": ["mall-portal", "mall-admin"], "replay": "T13",
                      "title": "토큰 exp 필수·iss/aud 확인(5-2). 서명을 금고로 옮긴 부분은 끌 수 없다"},
    "pii-mask": {"lesson": "s5-04", "service": ["mall-portal", "mall-admin"], "replay": "T14",
                 "title": "전화번호 검색용 해시·관리자 응답 가림·원문 보기 사유(5-4)"},
    # 섹션 6
    "secret-canary": {"lesson": "s6-02", "service": ["mall-portal", "mall-admin"], "replay": "T15",
                      "title": "미끼 자격증명(canary) 토큰 거부·이벤트(6-2)"},
    "startup-check": {"lesson": "s6-03", "service": ["mall-portal", "mall-admin"], "replay": "T15",
                      "title": "알려진 기본값 시크릿이면 기동 거부(6-3). 금고에서 받는 부분은 끌 수 없다"},
    "error-mask": {"lesson": "s6-05", "service": ["mall-portal", "mall-admin"], "replay": "T17",
                   "title": "DB 예외 문구를 응답에 싣지 않고 일반 문구+trace_id(6-5)"},
    "docs-closed": {"lesson": "s6-05", "service": ["mall-portal", "mall-admin"], "replay": "T17",
                    "title": "운영(prod)에서 API 문서 화면·문서 JSON 끄기(6-5). 관리 포트 분리는 끌 수 없다"},
    "cors-allowlist": {"lesson": "s6-06", "service": ["mall-portal", "mall-admin"], "replay": "—",
                       "title": "CORS 허용 출처 목록(자격증명은 목록에만)(6-6)"},
    "sec-headers": {"lesson": "s6-06", "service": ["mall-portal", "mall-admin"], "replay": "—",
                    "title": "API 응답 보안 헤더 4종(6-6). 화면 nginx 헤더는 끌 수 없다"},
    "cache-hmac": {"lesson": "s6-04", "service": "mall-admin", "replay": "—",
                   "title": "Redis 권한 캐시 서명 확인(6-4). 저장소 포트 닫기(A1)는 끌 수 없다"},
    "ip-trust": {"lesson": "s6-04", "service": ["mall-portal", "mall-admin"], "replay": "—",
                 "title": "믿는 프록시(게이트웨이·고객 웹)가 넣은 X-Forwarded-For 마지막 값만 클라이언트 IP로(6-4, E09). 끄면 늘 소켓 주소"},
    # 섹션 7
    "cache-type-allowlist": {"lesson": "s7-07", "service": ["mall-portal", "mall-admin"], "replay": "T18d(방어 확인)",
                             "title": "캐시 값의 형식 고정·허용 타입 목록(7-7). 끄면 저장소의 글자가 만들 타입을 고른다"},
    "signed-switches": {"lesson": "s7-mission", "service": ["mall-portal", "mall-admin"], "replay": "—",
                        "title": "서명 없는 방어 스위치 파일은 믿지 않는다(7-미션). 끄면 꺼짐 값을 서명 없이 믿는다"},
    # 섹션 8
    "audit-log": {"lesson": "s8-03", "service": "mall-admin", "replay": "T20",
                  "title": "관리자 조회 감사 로그(AUDIT)·대량 조회 data.read.bulk(8-3). 끄면 누가 몇 건을 봤는지 남지 않는다"},
    "status-codes": {"lesson": "s8-04", "service": ["mall-portal", "mall-admin"], "replay": "—",
                     "title": "인증 실패 HTTP 401·권한 없음 403과 auth.denied.token(8-4, F32). 끄면 옛 모습(HTTP 200 + 본문 code)"},
    "log-mask": {"lesson": "s8-05", "service": ["mall-portal", "mall-admin"], "replay": "T21",
                 "title": "로그 가리기: 요청 기록 원천 줄이기·변환기(8-5). 수집기 가리기는 끌 수 없다"},
    "audit-chain": {"lesson": "s8-06", "service": "mall-admin", "replay": "T20x(방어 확인)",
                    "title": "감사 로그 해시 체인 seq·prev·hash(8-6). 끄면 줄을 지워도 알 수 없다"},
    # 섹션 9
    "fail-closed": {"lesson": "s9-02", "service": "mall-portal", "replay": "T22",
                    "title": "본인 확인 서버 오류면 거부(503)·auth.fail_closed.dependency_error(9-2). 끄면 오류를 통과로"},
    "error-path": {"lesson": "s9-02", "service": ["mall-portal", "mall-admin"], "replay": "T46(방어 확인)",
                   "title": "/error 열기 + 처리되지 않은 예외는 500 + trace_id·error.unhandled(9-2, F46). 끄면 장애가 401로"},
    "input-limits": {"lesson": "s9-03", "service": ["mall-portal", "mall-admin"], "replay": "T23",
                     "title": "본문 1MB·pageSize 100·수량 1~99·쿠폰 코드 20자 + RE2/J(9-3). 끄면 상한 없음·옛 정규식"},
    "resilience": {"lesson": "s9-05", "service": "mall-portal", "replay": "T24",
                   "title": "결제 재시도 한 곳·백오프·지터·재시도 예산·서킷 브레이커·DLQ(9-5). 끄면 9-4의 재시도 폭주"},
    # 섹션 10
    "money-race": {"lesson": "s10-02", "service": "mall-portal", "replay": "T25",
                   "title": "쿠폰 수량·포인트 조건부 UPDATE + 쿠폰 1인 유니크 제약(10-2). 끄면 읽고-검사-쓰기·유니크 제약 지움(앱이 다시 뜰 때)"},
    "pay-webhook": {"lesson": "s10-04", "service": "mall-portal", "replay": "T26",
                    "title": "결제 성공 직접 호출 거절(410)·웹훅 서명→중복→금액→상태 전이(10-4). 끄면 웹훅이 검사 없이 결제 완료"},
    "reconcile": {"lesson": "s10-05", "service": ["mall-portal", "mall-admin"], "replay": "—",
                  "title": "포인트 원장 기록·매일 대사·ledger.mismatch(10-5). 끄면 원장을 남기지 않고 대사가 아무것도 알리지 않음"},
    "checkout-limits": {"lesson": "s10-06", "service": "mall-portal", "replay": "T27",
                        "title": "결제 시도 계정·카드 지문·IP 한도, 한정 상품 1인 한도, 실패율 경보(10-6). 끄면 한도 없음"},
    "sms-budget": {"lesson": "s10-07", "service": "mall-portal", "replay": "T27",
                   "title": "인증 문자 번호·IP·전체 시간당 예산, 국내 번호 형식만(10-7). 끄면 예산 없음"},
    "server-price": {"lesson": "s10-08", "service": "mall-portal", "replay": "T37",
                     "title": "장바구니·확인·생성에서 SKU 가격을 서버가 다시 읽기, price.tampered(10-8). 끄면 요청 가격을 믿음"},
    # 섹션 11. 11-3(게이트웨이 설정·A7)과 11-5(설정 점검)는 스위치가 없다(설정·구조라 끌 수 없다)
    "safe-redirect": {"lesson": "s11-02", "service": "mall-portal", "replay": "T28",
                      "title": "이동 주소를 한 번 파싱한 URI로 검사·이동, redirect.denied.not_allowed(11-2). 끄면 문자열 앞부분 비교"},
    "private-cache": {"lesson": "s11-04", "service": ["mall-portal", "varnish"], "replay": "T30",
                      "title": "인증 응답 private·no-store + Varnish 인증 요청 pass·오류 저장 안 함(11-4). 끄면 회사 설정 VCL(60초 강제 캐시)"},
    # 섹션 12. 파트너 입구(A9)·토큰 발급은 구조라 스위치가 없다
    "partner-scope": {"lesson": "s12-02", "service": "mall-portal", "replay": "T33",
                      "title": "@PartnerScope 범위 검사 + 조회 조건에 파트너(배정 주문만)(12-2). 끄면 범위·배정 조건 없이 모든 주문"},
    "token-binding": {"lesson": "s12-03", "service": "mall-portal", "replay": "T33",
                      "title": "파트너 토큰을 클라이언트 인증서에 묶기(cnf x5t#S256, 12-3). 끄면 무기명 토큰(누가 내밀든 통과)"},
    "partner-revoke": {"lesson": "s12-04", "service": "mall-portal", "replay": "T33",
                       "title": "앱 단위 일괄 폐기를 지키기(폐기 목록 deny:jti·폐기 앱 발급 거부, 12-4). 끄면 폐기한 앱의 토큰이 수명 동안 통과"},
    # 섹션 13. 승인 서비스 분리(A10)·서명 키 분리는 구조라 끌 수 없다. 승인 서비스(approval)도 같은 파일(.daon/switches.env)을 읽는다
    "approval": {"lesson": "s13-03", "service": ["mall-admin", "approval"], "replay": "T34",
                 "title": "반품 확인은 승인 서비스에 요청만·처리자는 로그인 정보·승인 토큰 확인 뒤 실행 + 승인 서비스의 남은 결제 금액·한도 정책(13-3). 끄면 요청 금액·처리자를 믿고 바로 환불"},
    "dual-approval": {"lesson": "s13-04", "service": "approval", "replay": "T34",
                      "title": "고액 두 사람 승인·메일 확인 번호·지연 실행 창·출금 서킷 브레이커(13-4). 끄면 고액도 정책만 맞으면 바로 승인"},
    "ops-elevation": {"lesson": "s13-06", "service": "mall-admin", "replay": "T35",
                      "title": "공용·데모 계정 닫기(같은 문구·auth.denied.disabled_account) + 고위험 기능은 시간 제한 권한 상승 뒤에만(13-6). 끄면 계정을 다시 열고 상시 권한으로 통과"},
    # 섹션 14. 14-2 불변 백업(A11: 다른 저장소·계정·망, 객체 잠금)은 구조라 끌 수 없다. 14-4 격리는 사고 대응 도구라 스위치가 없다
    "degraded-mode": {"lesson": "s14-03", "service": ["mall-portal", "mall-admin"], "replay": "R14-3(회귀)",
                      "title": "결제 확인 벌크헤드(동시 6자리·대기 0)·느린 호출 브레이커 + 축소 운영(결제 대기 큐·환불 503·출금 브레이커)(14-3). 끄면 결제사를 5초까지 기다리고 축소 운영 값을 무시"},
}
STATE = DATA / "switches.json"
ENV_FILE = DATA / "switches.env"       # infra/compose.yml의 x-switches가 읽는다(없으면 모두 켜짐)
ARCH_NAMES = {"a11": "A11 불변 백업(백업 전용 저장소 backup-vault · 객체 잠금 · backup-writer/restorer · backup 망). 14-2 시작 모습은 [회사 설정] 백업 작업", "a10": "A10 승인 서비스 분리(승인 서비스 8097 · 서명된 승인 토큰 · 전용 DB 계정)", "a09": "A9 파트너 입구(게이트웨이 8087 → /partner/**, 12-3부터 mTLS)", "a07": "A7 캐시와 게이트웨이 강화(고객 API 입구 8086 · Varnish · 모호한 요청 거절)", "a06": "A6 재시도 큐와 DLQ(RabbitMQ 정의 파일)", "a05": "A5 기록 모으기와 경보(Loki·Alloy·Grafana → Mailpit)", "a08": "A8 로컬 CI(Gitea·러너·레지스트리)", "a02": "A2 나가는 길 통제(이그레스 프록시)", "a03": "A3 Redis 접근 제어(ACL·앱별 사용자)", "a01": "A1 입구 정리(망 분리·게이트웨이·저장소 인증)"}
BANNER = "교육용 랩 · 127.0.0.1 · 값은 가짜"


# ── 방어 스위치 ──
def _has_service(stack: str, name: str) -> bool:
    """14-2: backup-audit는 A11 완료 모습에만 있다(시작 모습의 a11 파일에는 없다)"""
    r = st.compose(stack, "config", "--services", check=False, capture=True)
    return name in (r.stdout or "").split()


def switches() -> dict[str, bool]:
    try:
        saved = json.loads(STATE.read_text("utf-8"))
    except (FileNotFoundError, ValueError):
        saved = {}
    return {k: bool(saved.get(k, True)) for k in SWITCHES}


def env_name(key: str) -> str:
    return "SECUREKIT_" + key.upper().replace("-", "_") + "_ENABLED"


def _write(values: dict[str, bool]) -> None:
    DATA.mkdir(exist_ok=True)
    STATE.write_text(json.dumps(values, ensure_ascii=False, indent=2), "utf-8")
    lines = ["# make switch가 만든다. 손으로 고치지 않는다(랩-설계.md 12절). 여기 없는 스위치는 켜짐."]
    off = [f"{env_name(k)}=false" for k, on in values.items() if not on]
    lines += off
    if off:                                   # 7-미션: 꺼짐 값에는 서명을 붙인다. 앱은 서명이 맞지 않으면 꺼짐을 믿지 않고 기본값(켜짐)으로 뜬다
        lines.append(f"{SIG_NAME}={sign(off)}")
    ENV_FILE.write_text("\n".join(lines) + "\n", "utf-8")


def reached(key: str, pos: dict | None = None) -> bool:
    """지금 지점에서 그 스위치를 읽는 코드(정답)가 들어와 있나."""
    pos = pos if pos is not None else course.position()
    if not pos:
        return False
    ids = course.order()
    want, here = SWITCHES[key]["lesson"], pos["lesson"]
    if want not in ids or here not in ids:
        return False
    return ids.index(here) > ids.index(want) or (here == want and pos.get("done"))


def off_switches() -> list[str]:
    return [k for k, on in switches().items() if not on]


def warn_if_off() -> None:
    off = off_switches()
    if off:
        print(f"[알림] 꺼진 방어 스위치: {', '.join(off)} → 완료 지점 시험이 레드일 수 있습니다. 다시 켜기: make switch K=all V=on")


def cmd_switch(a) -> None:
    stack = a.stack or current_stack()
    values = switches()
    if not a.key:
        pos = course.position()
        print("방어 스위치(secure-kit.<이름>.enabled, 완료 지점 코드만 읽음)")
        for k, meta in SWITCHES.items():
            state = "켜짐" if values[k] else "꺼짐"
            where = "" if reached(k, pos) else "  (지금 지점에는 아직 이 코드가 없음)"
            print(f"  {k:<20} {state}  {meta['title']} · 재연 {meta['replay']}{where}")
        print("\n바꾸기: make switch K=ownership V=off  ·  전부 켜기: make switch K=all V=on")
        return
    keys = list(SWITCHES) if a.key == "all" else [a.key]
    for k in keys:
        if k not in SWITCHES:
            raise SystemExit(f"없는 스위치입니다: {k}. 있는 것: {', '.join(SWITCHES)}, all")
    v = (a.value or "").lower()
    if v not in ("on", "off"):
        raise SystemExit("V는 on 또는 off입니다: make switch K=ownership V=off")
    on = v == "on"
    changed = [k for k in keys if values[k] != on]
    for k in keys:
        values[k] = on
    _write(values)
    if not changed:
        print(f"이미 {'켜짐' if on else '꺼짐'}입니다: {', '.join(keys)}")
        return
    for k in changed:
        note = "" if reached(k) else "  ※ 지금 지점에는 이 스위치를 읽는 코드가 없습니다(그 수업 완료 지점에서 쓰세요: make goto L=… AT=done)"
        print(f"{k} → {'켜짐' if on else '꺼짐'} ({env_name(k)}{'' if on else '=false'}){note}")
    if stack != "spring":
        print("Express 스택은 아직 스위치를 읽는 코드가 없습니다(diffs/만 제공). 값만 저장했습니다.")
        return
    if a.no_restart:
        print("값만 저장했습니다. 다음 make up·check 때 반영됩니다.")
        return
    services = sorted({svc for k in changed for svc in services_of(k)})
    if not _running(stack, services):
        print("앱이 떠 있지 않아 값만 저장했습니다. make up 때 반영됩니다.")
        return
    st.ensure_fresh(stack)
    print(f"[{stack}] 스위치를 반영하려고 다시 띄웁니다: {', '.join(services)}")
    st.compose(stack, "up", "-d", "--no-deps", *services)
    st.wait_healthy(stack)
    replays = " · ".join(f"make replay T={SWITCHES[k]['replay'][1:]}" for k in changed)
    print(f"확인: {replays}  ({'그린이어야 합니다' if on else '레드여야 합니다'})")
    if not on:
        # T07은 두 겹(3-4 출력 인코딩 + 3-5 CSP)이라 한 겹만 끄면 재연은 그린으로 남는다
        if "html-sanitize" in changed and reached("headers") and values["headers"]:
            print("  ※ 3-5 이후에는 CSP(headers)가 두 번째 겹으로 막아 T07이 그린입니다. 레드를 보려면 make switch K=headers V=off도 함께")
        if "headers" in changed and values["html-sanitize"]:
            print("  ※ 출력 인코딩(html-sanitize)이 켜져 있어 T07은 그린입니다. CSP가 빠진 것은 make check L=3-5가 레드로 보여 줍니다")


def services_of(key: str) -> list[str]:
    svc = SWITCHES[key]["service"]
    return [svc] if isinstance(svc, str) else list(svc)


def _running(stack: str, services: list[str]) -> bool:
    r = st.compose(stack, "ps", "--format", "json", *services, check=False, capture=True)
    return bool((r.stdout or "").strip())


# ── 랩 상황판 ──
def _events(stack: str, limit: int = 50) -> list[dict]:
    services = st.app_services(stack) + (["gateway"] if stack == "spring" and "a07" in st.arch_layers() else []) \
        + (["backup-audit"] if stack == "spring" and _has_service(stack, "backup-audit") else [])   # 11-3·11-4 게이트웨이, 13장 승인 서비스(A10), 14-2 백업 감사(A11)
    out = []
    for svc in services:
        r = st.compose(stack, "logs", "--no-log-prefix", "--tail", "3000", svc, check=False, capture=True)
        for line in (r.stdout or "").splitlines():
            if " SECURITY " not in line or "{" not in line:
                continue
            try:
                e = json.loads(line[line.index("{"):])
            except ValueError:
                continue
            if isinstance(e, dict) and e.get("event"):
                e["service"] = svc
                out.append(e)

    def when(e):
        try:
            return datetime.fromisoformat(str(e.get("ts", "")).replace("Z", "+00:00")).timestamp()
        except ValueError:
            return 0.0
    out.sort(key=when, reverse=True)
    return out[:limit]


def state(stack: str | None = None) -> dict:
    stack = stack or current_stack()
    pos = course.position()
    lesson = None
    if pos:
        try:
            l = course.find(pos["lesson"])
            lesson = {"id": l["id"], "no": l["no"], "title": l["title"], "section": l["section"], "done": bool(pos.get("done"))}
        except SystemExit:
            lesson = {"id": pos["lesson"], "no": pos["lesson"], "title": "", "section": None, "done": bool(pos.get("done"))}
    layers = st.arch_layers()
    level = "A0" + "".join(f" + {n[0].upper()}{int(n[1:])}" for n in layers)
    values = switches()
    sw = [{"key": k, "title": m["title"], "lesson": course.short(m["lesson"]), "replay": m["replay"],
           "on": values[k], "active": reached(k, pos)} for k, m in SWITCHES.items()]
    score = None
    p = ROOT / "scoreboard" / f"{stack}.json"
    if p.exists():
        try:
            score = json.loads(p.read_text("utf-8"))
        except ValueError:
            score = None
    try:
        events, events_error = _events(stack), None
    except SystemExit as e:            # docker가 없을 때
        events, events_error = [], str(e)
    return {
        "banner": BANNER,
        "at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "stack": stack,
        "lesson": lesson,
        "arch": {"level": level, "layers": [{"name": n, "label": ARCH_NAMES.get(n, n)} for n in layers]},
        "switches": sw,
        "score": score,
        "events": events,
        "events_error": events_error,
    }


PAGE = Path(__file__).with_name("lab_page.html")
ALLOWED_HOSTS = {"127.0.0.1", "localhost"}


def serve(port: int, stack: str) -> None:
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

    cache: dict = {"t": 0.0, "body": b""}

    class Handler(BaseHTTPRequestHandler):
        def _send(self, code: int, body: bytes, ctype: str) -> None:
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Security-Policy",
                             "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):  # noqa: N802
            # 2-6에서 배운 대로: 이 PC 이름으로 부른 요청만 받는다(DNS 리바인딩 막기)
            host = (self.headers.get("Host") or "").rsplit(":", 1)[0].strip("[]").lower()
            if host not in ALLOWED_HOSTS:
                return self._send(403, "허용하지 않은 Host입니다".encode(), "text/plain; charset=utf-8")
            path = self.path.split("?", 1)[0]
            if path in ("/", "/lab", "/lab/"):
                return self._send(200, PAGE.read_bytes(), "text/html; charset=utf-8")
            if path == "/lab/state.json":
                if time.time() - cache["t"] > 2:
                    cache["body"] = json.dumps(state(stack), ensure_ascii=False).encode()
                    cache["t"] = time.time()
                return self._send(200, cache["body"], "application/json; charset=utf-8")
            return self._send(404, b"not found", "text/plain")

        def log_message(self, *args):   # 요청마다 찍지 않는다
            pass

    srv = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"랩 상황판: http://127.0.0.1:{port}/lab  (읽기 전용, 이 PC에서만 · 끝내려면 Ctrl+C)")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        srv.server_close()


def cmd_lab(a) -> None:
    stack = a.stack or current_stack()
    if a.json:
        print(json.dumps(state(stack), ensure_ascii=False, indent=2))
        return
    serve(int(a.port or ENV.get("DAON_LAB_PORT", 8099)), stack)


def register(add, stack_opt) -> None:
    """cli.main()이 부른다."""
    add("switch", cmd_switch, (["key"], {"nargs": "?"}), (["value"], {"nargs": "?"}), stack_opt,
        (["--no-restart"], {"action": "store_true"}))
    add("lab", cmd_lab, stack_opt, (["--json"], {"action": "store_true"}), (["--port"], {}))
