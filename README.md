# 다온마켓(daon-market) · 「해킹 서바이벌 가이드」 실습 저장소

가상의 쇼핑몰 회사 「다온마켓」의 백엔드입니다. 오픈소스 쇼핑몰 두 개(mall·EverShop 기반)를 포크했고, 내 PC 한 대에서 docker compose로 돌아갑니다.
**원본 mall은 중국어 프로젝트이고, 이 강의는 한국어로 번역한 판으로 진행합니다**(`spring/UPSTREAM.md`).

| 폴더 | 내용 | 라이선스 |
|---|---|---|
| `spring/` | mall 포크(Spring Boot 3.5, MyBatis): mall-admin(관리자 API)·mall-portal(고객 API)·mall-admin-web(관리자 화면) | Apache-2.0 |
| `express/` | EverShop 포크(Express·TypeScript·PostgreSQL) | GPL-3.0 |
| `infra/` | compose(아키텍처 A0부터), Dockerfile, 초기화 스크립트 | MIT |
| `seed/` | 가짜 데이터(계정·주문) | MIT |
| `replay/` · `tests/` | 재연 스크립트와 시험(HTTP, 127.0.0.1만) | MIT |
| `course/` · `solutions/` · `lessons/` | 수업 시작 상태, 수업별 정답, 수업 카드 | MIT |

## 설치

필요한 것: Docker Desktop·OrbStack·Rancher Desktop 중 하나, Python 3.11 이상, make(없으면 `python -m daon <명령>`). 메모리 16GB 권장, 8GB면 스택을 하나만 켭니다.

```bash
make setup               # 도구용 가상환경(.venv)
make up STACK=spring     # 처음엔 이미지 빌드로 15분쯤. 그다음부터는 30초 안
make up STACK=express    # EverShop. 처음 한 번 관리자 계정과 데모 상품을 넣습니다
```

PC에 이미 MySQL(3306) 같은 프로그램이 있어 포트가 겹치면 `infra/.env`에서 번호를 바꿉니다. `make up`이 겹치는 포트를 알려 줍니다.

| 주소 | 무엇 | 계정(가짜) |
|---|---|---|
| http://127.0.0.1:8085/swagger-ui.html | 고객 API(mall-portal) | alice / bob, 비밀번호 `daon-pass-1!` |
| http://127.0.0.1:8080/swagger-ui.html | 관리자 API(mall-admin) | admin / macro123 (원본 데모 계정) |
| http://127.0.0.1:8090 | 관리자 화면 | 위와 같음 |
| http://127.0.0.1:3000 · `/admin` | EverShop | admin@daon.example / `daon-admin-1!` |

## 수업 따라가기

```bash
make goto L=2-2          # 그 수업 시작 지점으로(완료 지점: AT=done)
make replay T=01         # 재연: [레드] 취약 / [그린] 막힘
make check L=2-2         # 수업 확인. 소스가 바뀌었으면 앱을 다시 빌드한 뒤 시험
make score               # 점수판: 취약 N개 / 36
make mem                 # 컨테이너별 메모리
make down                # 내리기(데이터 남김) · make wipe: 데이터까지 지우고 시드부터 다시
```

## 안전 범위

재연은 이 PC의 다온마켓(127.0.0.1)만 부르고, 값은 전부 가짜입니다. 시작 상태(A0)는 일부러 약하게 만든 구조라 PC 포트를 엽니다. 같은 와이파이의 다른 기기에서 닿지 않도록 기본 바인드 주소는 127.0.0.1입니다(`infra/.env`의 `DAON_BIND`). 바꾸지 마세요.
