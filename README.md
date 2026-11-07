# 다온마켓(daon-market) · 「해킹 서바이벌 가이드」 실습 저장소

가상의 쇼핑몰 회사 「다온마켓」의 백엔드입니다. 오픈소스 쇼핑몰 두 개(mall·EverShop 기반)를 포크했고, 내 PC 한 대에서 docker compose로 돌아갑니다.
**원본 mall은 중국어 프로젝트이고, 이 강의는 한국어로 번역한 판으로 진행합니다**(`spring/UPSTREAM.md`).

| 폴더 | 내용 | 라이선스 |
|---|---|---|
| `spring/` | mall 포크(Spring Boot 3.5, MyBatis): mall-admin(관리자 API)·mall-portal(고객 API)·mall-admin-web(관리자 화면) | Apache-2.0 |
| `express/` | EverShop 포크(Express·TypeScript·PostgreSQL) | GPL-3.0 |
| `infra/` | compose(아키텍처 A0부터), Dockerfile, 초기화 스크립트 | MIT |
| `seed/` | 가짜 데이터: 고객 40명·주문 300건, 두 스택 같은 번호(`seed/README.md`, `tools/gen_seed.py`) | MIT |
| `web/` | 다온마켓 고객 웹(작은 상점 화면, 고객 API 사용) | MIT |
| `stubs/` | 가짜 외부 서비스: 결제사·문자·택배·이미지 공급사, 메일은 Mailpit(`stubs/README.md`) | MIT |
| `replay/` · `tests/` | 재연 스크립트와 시험(HTTP, 127.0.0.1만) | MIT |
| `course/` · `lessons/` | 수업 시작 상태, 수업 카드 | MIT |

## 설치

필요한 것: Docker Desktop·OrbStack·Rancher Desktop 중 하나, Python 3.11 이상, make(없으면 `python -m daon <명령>`). 메모리 16GB 권장, 8GB면 스택을 하나만 켭니다.

```bash
make setup               # 도구용 가상환경(.venv)
make up STACK=spring     # 처음엔 이미지 빌드로 15분쯤(실측 약 13분). 그다음부터는 1분 안(실측 41초)
make up STACK=express    # EverShop. 처음 한 번 관리자 계정과 데모 상품을 넣습니다
```

PC에 이미 MySQL(3306) 같은 프로그램이 있어 포트가 겹치면 `infra/.env`에서 번호를 바꿉니다. `make up`이 겹치는 포트를 알려 줍니다.

| 주소 | 무엇 | 계정(가짜) |
|---|---|---|
| http://127.0.0.1:8088 | 다온마켓 고객 웹(로그인·상품·장바구니·주문, 주문 상세 `/orders/9001`) | alice / bob, 비밀번호 `daon-pass-1!` (고객 103~140은 `daon103`~`daon140`) |
| http://127.0.0.1:8085/swagger-ui.html | 고객 API(mall-portal) | alice / bob, 비밀번호 `daon-pass-1!` |
| http://127.0.0.1:8080/swagger-ui.html | 관리자 API(mall-admin) | admin / macro123 (원본 데모 계정) |
| 〃 | 관리자 API 직원 계정(2-3·2-5) | staff_goods(상품 관리자) / staff_order(주문 관리자), 비밀번호 `daon-pass-1!` |
| http://127.0.0.1:8095/ops/health | 다온 운영 도구(교육용, 로그인 없음, 2-6) | — |
| http://127.0.0.1:8090 | 관리자 화면 | 위와 같음 |
| http://127.0.0.1:3000 · `/admin` | EverShop | admin@daon.example / `daon-admin-1!` · 고객은 `alice@daon.example` 등(같은 비밀번호) |
| http://127.0.0.1:8025 | 가짜 메일함(Mailpit, SMTP 1025) | — |
| http://127.0.0.1:9101 · 9102 · 9103 | 가짜 결제사·문자·택배(`stubs/README.md`) | — |

모든 화면 아래에 「교육용 랩 · 127.0.0.1 · 값은 가짜」 띠가 붙습니다.

## 수업 따라가기

```bash
make goto L=2-2          # 그 수업 시작 지점으로(완료 지점: AT=done)
make replay T=01         # 재연: [레드] 취약 / [그린] 막힘
make check L=2-2         # 수업 확인. 소스가 바뀌었으면 앱을 다시 빌드한 뒤 시험
make score               # 점수판: 취약 N개 / 36
make mem                 # 컨테이너별 메모리
make down                # 내리기(데이터 남김)
make wipe                # 데이터를 시드 상태로(DB 스냅샷, Spring 약 3초 · Express 약 2초). HARD=1이면 볼륨째 지우고 처음부터
make switch K=ownership V=off   # 방어 스위치(완료 지점 전용, 영상의 끄면 레드·켜면 그린). K 없으면 목록
make lab                 # 랩 상황판 http://127.0.0.1:8099/lab (지점·단계·스위치·점수판·최근 보안 이벤트)
```

- 수업이 진행되면 아키텍처도 바뀝니다. 켠 단계는 `infra/arch.txt`에 한 줄씩 적히고(2-5부터 `a02` = 이그레스 프록시), `make goto`·`make check`가 알아서 맞춥니다.
- 보안 이벤트(⑤ 탐지) 로그는 `make logs S=mall-portal | grep SECURITY`로 봅니다. 이름 목록은 `course/SECURITY_EVENTS.md`.
- 모든 응답에는 요청 추적 번호 `X-Trace-Id`가 붙고, 같은 번호가 로그 줄 앞 `[…]`, 보안 이벤트의 `trace_id`, 큐 메시지 헤더에 남습니다(요청에 `X-Trace-Id`를 달아 보내면 그 값을 씁니다).
- 섹션 미션은 영상 대신 수업 노트입니다: `make lesson L=2-mission`, `make check L=2-mission`.
- Express(EverShop)에서 같은 곳을 고치는 법은 `diffs/<수업>/express.diff`(공통 도우미 `diffs/common/`).

## 안전 범위

재연은 이 PC의 다온마켓(127.0.0.1)만 부르고, 값은 전부 가짜입니다. 시작 상태(A0)는 일부러 약하게 만든 구조라 PC 포트를 엽니다. 같은 와이파이의 다른 기기에서 닿지 않도록 기본 바인드 주소는 127.0.0.1입니다(`infra/.env`의 `DAON_BIND`). 바꾸지 마세요.
