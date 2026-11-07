# SECURITY_EVENTS · 보안 이벤트 이름 목록

v0.1 · 2026-11-07 · 기준: `../../../랩-설계.md` 10절. **수업에서 이벤트를 하나 새로 만들면 여기에 한 줄 더한다.**

## 형식(두 스택 같음)

로거 이름 `SECURITY`, 한 줄 JSON. Spring은 `spring/mall-security/.../security/event/SecurityEventLogger.java`, Express는 `diffs/common/express-security-event.diff`의 `lib/log/securityEvent.ts`.

```json
{"event":"access.denied.not_owner","lesson":"2-2","actor":"member:102","target":"order:9001","ip":"172.27.0.1","ua":"python-httpx/0.28","reason":"본인 주문이 아니거나 없는 주문","count":1,"ts":"2026-11-07T14:10:00.123+09:00","trace_id":"…"}
```

| 키 | 내용 |
|---|---|
| `event` | 아래 표의 이름. `<영역>.<결과>.<이유>` |
| `lesson` | 그 이벤트를 넣은 수업 번호 |
| `actor` | 누가: `member:<ID>`, `admin:<계정 이름>`, `customer:<uuid>`, `anonymous` |
| `target` | 무엇을: ID만(`order:9001`, `path:/daon/customer/list`, `host:127.0.0.1`) |
| `ip` · `ua` | 요청 주소, User-Agent(120자까지) |
| `reason` | 사람이 읽는 이유(값이 아니라 설명) |
| `count` | 같은 `event`·`actor`가 최근 10분 안에 몇 번째인지(앱 인스턴스 하나 기준) |
| `ts` · `trace_id` | 시각(+09:00), 요청 추적 ID(`X-Request-Id` → `traceparent` → 새로 만듦) |

넣지 않는 것: 개인정보 원문(이름·전화·주소), 토큰·비밀번호, 요청 본문 전체, 보낸 값(예: 포인트 999999). 로그 수준: 거부 WARN, 의심 INFO.

## 이벤트 목록

| event | 수준 | 수업 | 남기는 곳(Spring) | 뜻 | 경보 후보(8장) |
|---|---|---|---|---|---|
| `access.denied.not_owner` | WARN | 2-2 | `OmsPortalOrderServiceImpl.detail()` | 남의 주문(또는 없는 주문) 조회를 거부 | 같은 actor `count` ≥ 5 / 10분(번호 훑기) |
| `access.denied.not_owner` | WARN | 2-mission | `OmsPortalOrderReturnApplyServiceImpl.create()` | 남의 주문으로 반품 신청을 거부 | 위와 같음 |
| `access.denied.no_permission` | WARN | 2-3 | `DynamicAuthorizationManager` | 로그인한 관리자가 권한표에 없는 주소·역할 밖 주소를 부름 | 같은 actor `count` ≥ 3 / 10분 |
| `input.unexpected_fields` | INFO | 2-4 | `UmsMemberServiceImpl.updateInfo()` | 요청 전용 DTO에 없는 항목이 섞인 수정 요청(무시하고 처리) | 한 번이라도(정상 화면은 보내지 않는다) |
| `egress.denied.internal_address` | WARN | 2-5 | `DaonImageFetcher` | 서버가 대신 부를 주소가 내부 주소 | 한 번이라도 |
| `egress.denied.scheme` · `egress.denied.bad_url` · `egress.denied.no_proxy` · `egress.denied.not_image` | WARN | 2-5 | `DaonImageFetcher` | 스킴·형식·프록시 없음·이미지 아님 | `no_proxy`는 설정 사고, 나머지는 반복 시 |
| `egress.denied.not_allowed_host` | WARN | 2-5(Express) | EverShop `secureFetch.ts` | 허용 목록 밖 호스트(리다이렉트 포함) | 반복 시 |
| `request.denied.host` | WARN | 2-6 | `DaonOpsGuardFilter` | 운영 도구에 허용하지 않은 Host(DNS 리바인딩 의심) | 한 번이라도 |

## 확인

수업마다 `make check`에 「재연 뒤 앱 로그에 그 event가 남았는지」 보는 시험이 하나 있다(`tests/test_s2_0N_*.py::test_*_logged`, `replay/_logs.py`). 이그레스 프록시는 따로 `make logs S=egress | grep TCP_DENIED`.
