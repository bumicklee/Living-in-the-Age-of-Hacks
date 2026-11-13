# 다온마켓 랩 명령. make가 없으면 같은 이름으로 `python -m daon <명령>`을 쓴다(Windows 등).
# 스택은 STACK=spring(기본)|express, 수업 번호는 L=2-2 처럼 넘긴다(02-02도 된다).
PY := .venv/bin/python
ifeq ($(OS),Windows_NT)
PY := .venv/Scripts/python
endif
STACK ?= spring
export STACK

.PHONY: scan-secrets hooks help setup up down wipe ps logs mem goto lesson status where next doctor check check-all solution apply-solution replay score verify-course switch lab sbom kev upstream-sim upstream-status ci-lint ci-build ci-run ci-sim-tag-move ci-sim-image-swap ci-runner-status deploy redis-flush events-count alert-rules collector-test log-scan audit-verify traffic-normal drill ack drill-report wipe-logs stub-fault measure measure-cooldown config-check ledger-break ledger-fix reconcile money-reset audit-gateway audit-dns audit-mail cache-purge partner-app partner-certs partner-audit partner-revoke partner-reset approval-status approval-reset backup-now backup-status restore-drill evidence timeline capstone restore-columns audit final-score kit-up kit-down kit-contract public-export

help:             ## 명령 목록
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t/'

setup:            ## 처음 한 번: 도구용 가상환경(재연·시험, 관리자 화면 시험용 Chromium)
	@command -v uv >/dev/null 2>&1 && uv venv -q .venv && uv pip install -q --python $(PY) -r requirements.txt \
	  || (python3 -m venv .venv && $(PY) -m pip install -q -r requirements.txt)
	@$(PY) -m playwright install chromium >/dev/null || echo "Chromium 설치 실패: 관리자 화면 브라우저 시험(3-4·3-5)만 「준비 안 됨」이 됩니다"
	@test -f infra/.env || cp infra/.env.example infra/.env
	@echo "준비 끝. 다음: make up STACK=spring (또는 STACK=express)"

up: $(PY)         ## 스택 띄우기: make up STACK=spring|express (BUILD=1이면 다시 빌드)
	@$(PY) -m daon up --stack $(STACK) $(if $(BUILD),--build)
down: $(PY)       ## 스택 내리기(데이터는 남김): make down [STACK=…]
	@$(PY) -m daon down $(if $(filter command line,$(origin STACK)),--stack $(STACK))
wipe: $(PY)       ## 데이터를 시드 상태로(스냅샷, 몇 초). HARD=1이면 볼륨까지 지우고 처음부터: make wipe [STACK=…]. 증거를 모으지 않은 사고 기록이 있으면 멈춘다(FORCE=1, 14-4)
	@$(PY) -m daon down --wipe $(if $(HARD),--hard) $(if $(FORCE),--force) $(if $(filter command line,$(origin STACK)),--stack $(STACK))
ps: $(PY)         ## 컨테이너 상태
	@$(PY) -m daon ps --stack $(STACK)
logs: $(PY)       ## 로그: make logs S=mall-portal
	@$(PY) -m daon logs $(S)
mem: $(PY)        ## 컨테이너별 메모리
	@$(PY) -m daon mem

peek: $(PY) ## 랩 전용(영상용): 관리자 응답 보기(가짜 회원만): make peek W=list|search|order|reveal A=값 R=사유
	@$(PY) -m daon peek $(W) $(A) $(R)
unit-test: ## 단위 시험(5-2 JwtAlgorithmPinTest): Docker의 Maven으로 mall-security 시험만 돌린다
	@docker run --rm -v "$(CURDIR)/spring":/src -v daon-m2:/root/.m2 -w /src maven:3.9.12-eclipse-temurin-17 mvn -B -pl mall-security -am test -Dtest=JwtAlgorithmPinTest -Dsurefire.failIfNoSpecifiedTests=false -Ddocker.skip=true -DskipTests=false 2>&1 | grep -E "Tests run:|ERROR|BUILD"
rotate-key: $(PY) ## 랩 전용: 토큰 서명 키 회전·폐기(5-2): make rotate-key K=jwt ACTION=rotate|retire|reset|status
	@$(PY) -m daon rotate-key $(or $(K),jwt) $(or $(ACTION),rotate)
scan-secrets: $(PY) ## 6-2 시크릿 점검(값은 가림): make scan-secrets [HISTORY=1] [IMAGE=mall-portal] [RULES=default]
	@$(PY) -m daon scan-secrets $(if $(HISTORY),--history) $(if $(IMAGE),--image $(IMAGE)) $(if $(RULES),--rules $(RULES))
hooks: $(PY)      ## 6-2 커밋 전 시크릿 점검 훅 설치(.git/hooks/pre-commit)
	@$(PY) -m daon hooks
dump-db: $(PY)    ## 랩 전용: DB·캐시에 평문 개인정보가 몇 곳인지(건수만, 5-3)
	@$(PY) -m daon dump-db

goto: $(PY)       ## 수업 시작 지점으로: make goto L=2-2 (완료 지점: AT=done)
	@$(PY) -m daon goto $(L) $(AT)
lesson: $(PY)     ## 수업 카드: make lesson L=2-2
	@$(PY) -m daon lesson $(L)
status: $(PY)     ## 지금 지점과 수업 목록
	@$(PY) -m daon status
where: $(PY)      ## 지금 수업 지점과 카드 보는 법
	@$(PY) -m daon where
next: $(PY)       ## 지금 지점에서 다음에 칠 명령
	@$(PY) -m daon next
doctor: $(PY)     ## 랩이 안 될 때 먼저 점검(Docker·메모리·컨테이너·스위치)
	@$(PY) -m daon doctor
check: $(PY)      ## 수업 확인(재연은 막히고 정상 요청은 통과): make check L=2-2
	@$(PY) -m daon check $(L) --stack $(STACK)
check-all: $(PY)  ## 시험 전부
	@$(PY) -m daon check --stack $(STACK)
solution: $(PY)   ## 내 코드와 정답의 차이: make solution L=2-2
	@$(PY) -m daon solution $(L)
apply-solution: $(PY) ## 정답으로 덮어쓰기(내 파일은 .daon/backup/에): make apply-solution L=2-2
	@$(PY) -m daon apply-solution $(L)
replay: $(PY)     ## 재연: make replay T=01 (T를 비우면 전부). 10장 T27은 PART=card|hoard|sms, 12장 T33은 PART=scope|reuse|export, 13장 T34는 PART=remaining|high-value. 회귀 재연: make replay R=14-3
	@DAON_PART=$(PART) $(PY) -m daon replay $(or $(T),$(R)) --stack $(STACK)
score: $(PY)      ## 점수판: 취약 N개 / 37
	@$(PY) -m daon score --stack $(STACK)
verify-course: $(PY) ## 강사용: 모든 수업이 시작=레드, 완료=그린인지(ONLY=2-2로 한 수업만)
	@$(PY) -m daon verify-course --stack $(STACK) $(if $(ONLY),--only $(ONLY))
switch: $(PY)     ## 방어 스위치(완료 지점 전용, 영상의 끄면 레드·켜면 그린): make switch K=ownership V=off (K 없으면 목록, K=all)
	@$(PY) -m daon switch $(K) $(V) --stack $(STACK)
lab: $(PY)        ## 랩 상황판 http://127.0.0.1:8099/lab (읽기 전용, Ctrl+C로 끝)
	@$(PY) -m daon lab --stack $(STACK)

$(PY):
	@$(MAKE) --no-print-directory setup

sbom: $(PY)       ## 7-2 SBOM 만들기(CycloneDX): build/sbom/*.cdx.json
	@$(PY) -m daon sbom
kev: $(PY)        ## 7-2 SBOM과 KEV 사본 대조 + 결정 기록(security/vuln-decisions.yaml) 확인 (KEV=real 이면 실제 CISA 사본)
	@$(PY) -m daon kev $(if $(KEV),--kev $(KEV))
upstream-sim: $(PY) ## 7-3 가짜 원본 저장소(.daon/upstream.git)와 보안 패치 커밋 하나 만들기
	@$(PY) -m daon upstream-sim
upstream-status: $(PY) ## 7-3 원본 보안 패치 반영 여부·걸린 시간 (DAON_FAKE_NOW=+74h 로 시계를 돌려 본다)
	@$(PY) -m daon upstream-status
ci-lint: $(PY)    ## 7-5 CI 파일의 외부 단계가 커밋 해시로 고정돼 있는지(tools/ci/check_pins.py)
	@$(PY) -m daon ci-lint
ci-build: $(PY)   ## 7-5 빌드 단계(러너 밖): 이미지 파일(tar) 만들기
	@$(PY) -m daon ci-build
ci-run: $(PY)     ## 7-5 CI 저장소에 올리고 작업 결과 보기(A8이 켜져 있어야 함)
	@$(PY) -m daon ci-run
ci-sim-tag-move: $(PY) ## 7-5 재연: 로컬 Gitea 안에서 외부 단계의 v1 태그를 다른 커밋으로 옮김 (UNDO=1이면 되돌림)
	@$(PY) -m daon ci-sim-tag-move $(if $(UNDO),--undo)
ci-sim-image-swap: $(PY) ## 7-6 재연: CI 밖에서 같은 태그에 다른 이미지를 올림
	@$(PY) -m daon ci-sim-image-swap
ci-runner-status: $(PY) ## 7-5 러너 상태와 권한
	@$(PY) -m daon ci-runner-status
deploy: $(PY)     ## 7-6 스테이징 칸에 이미지 띄우기(tools/deploy/deploy.sh): make deploy [SERVICE=mall-portal]
	@$(PY) -m daon deploy $(SERVICE)
redis-flush: $(PY) ## 7-7 Redis의 mall:* 캐시 키 비우기(예전 형식의 값 정리)
	@$(PY) -m daon redis-flush

events-count: $(PY) ## 8-1 지금까지 쌓인 보안 이벤트(앱 SECURITY 줄 + 도구 이벤트)를 이름별로 센다
	@$(PY) -m daon events-count
alert-rules: $(PY) ## 8-4 SECURITY_EVENTS.md 표 → 경보 규칙(infra/observability/rules/security.yaml), Grafana 다시 띄움
	@$(PY) -m daon alert-rules
collector-test: $(PY) ## 8-4·8-5 앱 로그에 샘플 줄(SECURITY 줄·새는 줄)을 넣고 Loki에 어떻게 들어갔는지 본다
	@$(PY) -m daon collector-test
log-scan: $(PY)   ## 8-5 로그 속 평문 비밀번호·JWT·휴대전화 모양 줄 수(값은 가림): make log-scan [SINCE=10m]
	@$(PY) -m daon log-scan $(if $(SINCE),--since $(SINCE))
audit-verify: $(PY) ## 8-6 감사 로그 해시 체인·머리 해시(Loki 사본) 확인. 끊기면 종료 코드 1 + audit.chain.broken
	@$(PY) -m daon audit-verify
traffic-normal: $(PY) ## 8-4 평범한 관리자·회원 사용 흉내(오탐 확인): make traffic-normal [MIN=10]
	@$(PY) -m daon traffic-normal $(if $(MIN),--minutes $(MIN))
drill: $(PY)      ## 8-7 모의 경보 훈련(번호 훑기·대량 조회·감사 로그 줄 지우기 + 정상 사용): make drill [WAIT=150] · 14-4 모의 침해 재생: make drill D=14-4
	@$(PY) -m daon drill $(if $(WAIT),--wait $(WAIT)) $(if $(D),--drill $(D))
ack: $(PY)        ## 8-7 경보 확인 기록: make ack ID=access.denied.not_owner
	@$(PY) -m daon ack $(ID)
drill-report: $(PY) ## 8-7 경보 → 확인 시간 표. 기준 시간을 넘긴 P1은 다른 담당에게 다시 알림
	@$(PY) -m daon drill-report
wipe-logs: $(PY)  ## 랩 전용: Loki에 쌓인 로그 지우기(8-5에서 가리기 전 줄을 비울 때)
	@$(PY) -m daon wipe-logs

stub-fault: $(PY) ## 9-2·9-4 가짜 스텁에 장애 넣기·빼기: make stub-fault S=sms|pay MODE=500|slow|ok [MS=3000]
	@$(PY) -m daon stub-fault $(S) $(MODE) $(if $(MS),--ms $(MS))
measure: $(PY)    ## 9-1·9-4 랩 안 측정(상한 고정): make measure M=regex (길이별 처리 시간) | M=retry (재시도 증폭 배수, 약 75초)
	@$(PY) -m daon measure $(M)
measure-cooldown: $(PY) ## 9-1 측정 뒤 mall-portal CPU가 내려갈 때까지 기다림(최대 60초)
	@$(PY) -m daon measure-cooldown
config-check: $(PY) ## 9-3 설정 점검(공격 트래픽 없음): make config-check C=http2
	@$(PY) -m daon config-check $(C)

ledger-break: $(PY) ## 10-5 랩 전용: 원장 없이 회원 포인트만 바꿈(대사가 찾을 불일치): make ledger-break [M=101]
	@$(PY) -m daon ledger-break $(M)
ledger-fix: $(PY) ## 10-5 랩 전용: 회원 포인트를 원장 합계로 되돌림: make ledger-fix [M=101]
	@$(PY) -m daon ledger-fix $(M)
reconcile: $(PY)  ## 10-5 대사 지금 실행(관리자 앱, 10-5 정답 뒤): 불일치 회원·주문 ID
	@$(PY) -m daon reconcile
money-reset: $(PY) ## 10장 재연 뒤 랩 값 되돌리기(alice·bob 포인트·쿠폰 이력·한정 상품 카운터·rl:* 카운터)
	@$(PY) -m daon money-reset

audit-gateway: $(PY) ## 11-3 게이트웨이·앱 구간 설정 점검(모호한 요청 거절·연결 재사용·버전·다이제스트, 공격 요청은 보내지 않음)
	@$(PY) -m daon audit-gateway
audit-dns: $(PY)  ## 11-5 DNS 영역 파일 점검(주인 없는 레코드·SPF include·와일드카드, 실제 DNS 조회 없음). CI 검사 작업도 같은 파일
	@$(PY) -m daon audit-dns
audit-mail: $(PY) ## 11-5 메일 서버 설정 사본·DMARC 점검(infra/mail/postfix-main.cf, 메일은 보내지 않음)
	@$(PY) -m daon audit-mail
cache-purge: $(PY) ## 11-4 Varnish 캐시 비우기(재연을 연달아 돌리기 전에)
	@$(PY) -m daon cache-purge

partner-app: $(PY) ## 12장 가짜 파트너 앱 한 번(건수만 찍음): make partner-app [APP=courier-01] [DO=orders|export|token]
	@$(PY) -m daon partner-app --app $(or $(APP),courier-01) --do $(or $(DO),orders)
partner-certs: $(PY) ## 12-3 랩 내부 CA가 만든 파트너 인증서를 .daon/partner-pki/로 가져오기(git에 넣지 않음)
	@$(PY) -m daon partner-certs
partner-audit: $(PY) ## 12-4 연동 앱 대장 점검(오래 안 쓴 앱·내보내기 범위·담당자 없음·회전 90일)
	@$(PY) -m daon partner-audit
partner-revoke: $(PY) ## 12-4 앱 단위 일괄 폐기(비밀값 둘 + 발급 토큰): make partner-revoke APP=courier-01
	@$(PY) -m daon partner-revoke $(APP)
partner-reset: $(PY) ## 랩 전용: 시드 파트너 셋을 처음 상태로(상태·범위·비밀값 v1)
	@$(PY) -m daon partner-reset

approval-status: $(PY) ## 13장 승인 서비스(A10)·출금 서킷 브레이커·승인 요청 상태별 건수·가짜 결제사 환불 건수
	@$(PY) -m daon approval-status
approval-reset: $(PY) ## 랩 전용: 승인 기록·브레이커·시험용 반품 신청·권한 상승을 처음 상태로(재연·시험을 처음부터)
	@$(PY) -m daon approval-reset

backup-now: $(PY) ## 14장 백업 작업을 지금 한 번(backup-job 안 backup.sh)
	@$(PY) -m daon backup-now
backup-status: $(PY) ## 14-2 잠금 버킷의 최근 백업·보존 모드·기한, backup-audit 상태(읽기만 되는 backup-restorer로 본다)
	@$(PY) -m daon backup-status
restore-drill: $(PY) ## 14-2 복구 훈련: 최신 백업 → 해시 확인 → 새 컨테이너 mysql-restore에 복구 → 확인 시험 → RTO·RPO
	@$(PY) -m daon restore-drill
evidence: $(PY)   ## 14-4 증거 보존: 로그·DB 스냅샷·이미지 다이제스트 → sha256 목록 → 잠금 버킷: make evidence INCIDENT=INC-14-4
	@$(PY) -m daon evidence --incident $(or $(INCIDENT),INC-LAB)
timeline: $(PY)   ## 14-4 사고 타임라인: make timeline ACTOR=member:117 [SINCE=30m] [PREFIX=drill-14-4-] [INCIDENT=INC-14-4] (python -m daon timeline 과 같다)
	@$(PY) -m daon timeline $(if $(ACTOR),--actor $(ACTOR)) $(if $(TRACE),--trace $(TRACE)) $(if $(PREFIX),--trace-prefix $(PREFIX)) $(if $(SINCE),--since $(SINCE)) $(if $(INCIDENT),--incident $(INCIDENT))

capstone: $(PY)   ## 15-1·15-2 캡스톤(가상 사고, 앞 장 재연만 묶음): make capstone [PART=1|status|answer] [RESET=1]. 이미 사고가 있으면 PART=1은 「다시 돌리기」
	@$(PY) -m daon capstone $(if $(PART),--part $(PART)) $(if $(RESET),--reset) --stack $(STACK)
restore-columns: $(PY) ## 15-2 골라 복구: make restore-columns ACTOR=admin:staff_night SINCE=<사고 시작 UTC> [APPLY=1 CONFIRM=9204,9205]
	@$(PY) tools/restore_columns.py $(if $(ACTOR),--actor $(ACTOR)) $(if $(SINCE),--since $(SINCE)) $(if $(ORDERS),--orders $(ORDERS)) $(if $(APPLY),--apply) $(if $(CONFIRM),--confirm $(CONFIRM))
audit: $(PY)      ## 15-3 최종 점검표 74행 다시 돌리기(course/audit.yaml): make audit [ONLINE=1]
	@$(PY) -m daon audit $(if $(ONLINE),--online) --stack $(STACK)
final-score: $(PY) ## 15-3 같은 재연으로 시작 지점(1-12)과 최종 지점(15-3 완료) 점수판 비교(앱을 두 번 다시 빌드)
	@$(PY) -m daon final-score --stack $(STACK)
kit-up: $(PY)     ## 15-4 secure-kit 예제 앱 빌드·실행(127.0.0.1:18181~18184): make kit-up [APPS=spring-shop,express-shop]
	@$(PY) -m daon kit-up $(if $(APPS),--apps $(APPS))
kit-down: $(PY)   ## 15-4 예제 앱 컨테이너 지우기
	@$(PY) -m daon kit-down
kit-contract: $(PY) ## 15-4 계약 시험(부품 24개 × 두 스택 + 가게 셋): make kit-contract [APPS=spring-demo,express-demo]
	@$(PY) -m daon kit-contract $(if $(APPS),--apps $(APPS))
public-export: ## 공개 저장소로 내보내기(복사 + 홈 경로·개인키·토큰·실제 전화번호 모양 검사, push는 하지 않음): make public-export [DEST=…]
	@tools/sync_public_repo.sh $(DEST)
