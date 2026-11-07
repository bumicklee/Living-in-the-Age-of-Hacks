# 다온마켓 랩 명령. make가 없으면 같은 이름으로 `python -m daon <명령>`을 쓴다(Windows 등).
# 스택은 STACK=spring(기본)|express, 수업 번호는 L=2-2 처럼 넘긴다(02-02도 된다).
PY := .venv/bin/python
ifeq ($(OS),Windows_NT)
PY := .venv/Scripts/python
endif
STACK ?= spring
export STACK

.PHONY: help setup up down wipe ps logs mem goto lesson status check check-all solution apply-solution replay score verify-course

help:             ## 명령 목록
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | sed 's/:.*## /\t/'

setup:            ## 처음 한 번: 도구용 가상환경(재연·시험)
	@command -v uv >/dev/null 2>&1 && uv venv -q .venv && uv pip install -q --python $(PY) -r requirements.txt \
	  || (python3 -m venv .venv && $(PY) -m pip install -q -r requirements.txt)
	@test -f infra/.env || cp infra/.env.example infra/.env
	@echo "준비 끝. 다음: make up STACK=spring (또는 STACK=express)"

up: $(PY)         ## 스택 띄우기: make up STACK=spring|express (BUILD=1이면 다시 빌드)
	@$(PY) -m daon up --stack $(STACK) $(if $(BUILD),--build)
down: $(PY)       ## 스택 내리기(데이터는 남김): make down [STACK=…]
	@$(PY) -m daon down $(if $(filter command line,$(origin STACK)),--stack $(STACK))
wipe: $(PY)       ## 내리고 데이터까지 지우기(시드부터 다시): make wipe [STACK=…]
	@$(PY) -m daon down --wipe $(if $(filter command line,$(origin STACK)),--stack $(STACK))
ps: $(PY)         ## 컨테이너 상태
	@$(PY) -m daon ps --stack $(STACK)
logs: $(PY)       ## 로그: make logs S=mall-portal
	@$(PY) -m daon logs $(S)
mem: $(PY)        ## 컨테이너별 메모리
	@$(PY) -m daon mem

goto: $(PY)       ## 수업 시작 지점으로: make goto L=2-2 (완료 지점: AT=done)
	@$(PY) -m daon goto $(L) $(AT)
lesson: $(PY)     ## 수업 카드: make lesson L=2-2
	@$(PY) -m daon lesson $(L)
status: $(PY)     ## 지금 지점과 수업 목록
	@$(PY) -m daon status
check: $(PY)      ## 수업 확인(재연은 막히고 정상 요청은 통과): make check L=2-2
	@$(PY) -m daon check $(L) --stack $(STACK)
check-all: $(PY)  ## 시험 전부
	@$(PY) -m daon check --stack $(STACK)
solution: $(PY)   ## 내 코드와 정답의 차이: make solution L=2-2
	@$(PY) -m daon solution $(L)
apply-solution: $(PY) ## 정답으로 덮어쓰기(내 파일은 .daon/backup/에): make apply-solution L=2-2
	@$(PY) -m daon apply-solution $(L)
replay: $(PY)     ## 재연: make replay T=01 (T를 비우면 전부)
	@$(PY) -m daon replay $(T) --stack $(STACK)
score: $(PY)      ## 점수판: 취약 N개 / 36
	@$(PY) -m daon score --stack $(STACK)
verify-course: $(PY) ## 강사용: 모든 수업이 시작=레드, 완료=그린인지(ONLY=2-2로 한 수업만)
	@$(PY) -m daon verify-course --stack $(STACK) $(if $(ONLY),--only $(ONLY))

$(PY):
	@$(MAKE) --no-print-directory setup
