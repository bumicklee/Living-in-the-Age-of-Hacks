# BASELINE · 시작 상태의 취약점 목록

v0.1 · 2026-11-07 · 기준: `../../../교육용-소스-설계.md` 4절. **출처 칸을 확인하기 전에는 수업에서 "원본 결함"이라고 말하지 않는다.**

| 출처 표시 | 뜻 |
|---|---|
| [원본 기본값] | 원본 데모 설정 그대로 |
| [회사 설정] | 다온마켓을 회사처럼 세팅하며 넣은 흔한 운영 실수(주로 `infra/compose.yml`) |
| [교육용 추가] | 수업을 위해 일부러 넣은 코드 |
| [원본 확인됨] | 포크 점검 중 원본에서 확인한 결함. **공개 전에 원작자에게 비공개 제보할지 먼저 정한다** |

| T | 취약점 | 수업 | 출처 | 재연 | 상태(2026-11-07) |
|---|---|---|---|---|---|
| T01 | 주문 상세 조회에 소유자 확인 없음(IDOR) | 2-1·2-2 | **[원본 확인됨] 후보**: mall `OmsPortalOrderServiceImpl.detail()`이 `selectByPrimaryKey`만 하고 회원을 확인하지 않는다(같은 파일의 `deleteOrder`·`cancelUserOrder` 등은 확인한다). 원본 커밋 `dcaa93b` | `replay/t01_order_idor.py` | Spring 레드 확인, 2-2 정답 적용 시 그린. **작성자 결정(2026-11-07): 원작자에게 제보하지 않음.** 강의에서는 원본을 지목하지 않고 「흔한 패턴」으로 다룬다 |
| T02 | 관리자 기능이 역할 확인 없이 열림 | 2-3 | **[교육용 추가]** 두 가지: ① 새 기능 `DaonCustomerController`(`/daon/customer/list`, 회원 이름·전화·포인트)를 권한표(`ums_resource`)에 등록하지 않음, ② `DynamicAuthorizationManager`를 「권한표에 없는 주소는 로그인만 했으면 통과」로 바꿈. **원본(`dcaa93b`)은 권한표에 없는 주소를 거부한다(점검 확인)** — 강의에서 원본 결함이라고 말하지 않는다 | `replay/t02_admin_role.py` | Spring: 시작 레드(staff_goods가 고객 목록 받음), 2-3 정답에서 그린. 직원 시드 staff_goods·staff_order 추가. Express: `diffs/s2-03/express.diff`(EverShop은 역할이 비면 `*`로 보는 원본 동작) |
| T03 | 회원정보 수정으로 등급·포인트 변경(대량 할당) | 2-4 | **[교육용 추가]** `POST /sso/updateInfo`가 `UmsMember` 엔티티를 그대로 받아 `updateByPrimaryKeySelective`(원본에는 회원정보 수정 API가 없다) | `replay/t03_mass_assignment.py` | Spring: 시작 레드(포인트·성장치·등급 변경, 재연이 원래 값으로 되돌림), 2-4 정답(요청 전용 DTO)에서 그린. Express: `diffs/s2-04/express.diff`(EverShop 원본은 보호 필드를 빼는 denylist, 등급·상태는 이미 막음) |
| T04 | 상품 이미지 URL 가져오기 SSRF | 2-5 | **[교육용 추가]** `GET /product/image/fetch?url=`(`DaonImageFetchController`·`DaonImageFetcher`) + **[회사 설정]** A0에서 관리자 앱이 랩 「인터넷」(203.0.113.0/24)으로 바로 나감 | `replay/t04_ssrf.py` | Spring: 시작 레드(127.0.0.1:8095 운영 도구 설정 값·mall-portal health), 2-5 정답(코드 검사 + A2 이그레스 프록시)에서 그린. Express: `diffs/s2-05/express.diff`(EverShop 원본은 이미 호스트 허용 목록) |
| T05 | 로컬 관리 도구 Host 확인 없음(DNS 리바인딩) | 2-6 | **[교육용 추가]** 다온 운영 도구(`/ops/**`, 관리자 앱 8095 포트, 로그인 없음, `secure.ignored.urls`에 `/ops/**`) | `replay/t05_dns_rebinding.py`(DNS 대신 Host 헤더로 같은 요청을 만든다) | Spring: 시작 레드, 2-6 정답(Host 허용 목록)에서 그린. Express: `diffs/s2-06/express.diff` |
| T01-M | 반품 신청에 주문 소유자 확인 없음(섹션 2 미션) | 2-mission | **[원본 확인됨] 후보**: mall `OmsPortalOrderReturnApplyServiceImpl.create()`가 본문의 `orderId`·`memberUsername`·`orderSn`을 확인 없이 저장한다. 배송지(`UmsMemberReceiveAddressServiceImpl`)는 원본이 이미 회원 ID 조건을 넣는다(확인) | 시험만(`tests/test_s2_mission_return_owner.py`) | Spring: 시작 레드, 미션 정답에서 그린. **작성자 결정 필요: T01과 같이 제보하지 않고 「흔한 패턴」으로 다룰지** |
| T06 | 정렬·검색 조건 문자열 조립 | 3-2·3-3 | 점검 후 결정 | — | |
| T07 | 상품 후기 저장형 XSS(관리자 화면) | 3-4·3-5 | 점검 후 결정 | — | |
| T08 | 파일 업로드 검사 부족 | 3-6 | 점검 후 결정 | — | |
| T09 | 로그인 실패 제한 없음, 유출 비밀번호 허용 | 4-2·4-3 | 점검 후 결정 | — | |
| T10 | 회원 여부에 따른 응답 시간 차 | 4-4 | 점검 후 결정 | — | |
| T11 | 재설정 링크 주소를 요청 헤더로 만듦 | 4-5 | 교육용 추가 | — | |
| T12 | 고위험 작업 재인증 없음, SMS 단독 | 4-7 | 교육용 추가 | — | |
| T13 | 고정 JWT 서명값, 7일 수명 | 5-2·4-8 | [원본 기본값] `jwt.secret: mall-portal-secret`, `expiration: 604800` | — | |
| T14 | 개인정보 컬럼 평문 저장 | 5-3·5-4 | 점검 후 결정 | — | |
| T15 | 설정 파일·git 이력의 시크릿 | 6-2·6-3 | [원본 기본값] + [회사 설정] | — | |
| T16 | Redis·MongoDB·RabbitMQ·MinIO가 비밀번호 없이(기본값으로) PC에 열림 | 6-4 | [회사 설정] `infra/compose.yml` A0 | `replay/t16_open_stores.py` | Spring 레드 확인(4곳) |
| T17 | 오류 응답 스택 추적, 관리용 엔드포인트 노출 | 6-5 | 점검 후 결정(`/actuator/**`·`/druid/**`가 원본 허용 목록에 있음) | — | |
| T18~T36 | 교육용-소스-설계.md 4절 표 그대로 | 7~14장 | | — | |

- 모든 막기에는 ⑤ 탐지 로그가 함께 있다. 이벤트 이름은 `SECURITY_EVENTS.md`.
- 2-5부터 아키텍처 A2(`infra/arch.txt`의 `a02`, `infra/compose.a02.yml`, `infra/egress/squid.conf`). 2-5 시작 지점은 A0.
- T01은 쇼핑몰 화면으로도 보인다(2-1 캡처): bob으로 로그인 → 주소창의 주문 번호를 9001로.
- 시드 계정: alice·bob(비밀번호 `daon-pass-1!`, `seed/spring/20-daon-accounts.sql`), 직원 staff_goods(상품 관리자)·staff_order(주문 관리자)(같은 비밀번호, 같은 파일). 원본 데모 계정(admin / macro123 등)은 그대로 남아 있다.
