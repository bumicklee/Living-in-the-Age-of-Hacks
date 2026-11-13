# 캡스톤 타임라인(15-1 정답 견본) · 가상 시나리오, 값은 가짜

<!-- 정답 견본: 시각·커밋은 make capstone을 돌릴 때마다 달라서 견본에는 「실행마다 다름」으로 둔다. 수강생은 실제 값을 적는다 -->

## 핵심 칸

| 칸 | 값 |
|---|---|
| 침입 계정 | admin:staff_night |
| 유출 커밋 | 실행마다 다름(make capstone PART=answer) |
| 유출 시작 시각 | 실행마다 다름(make capstone PART=answer) |
| 대량 조회 건수 | 300 |
| 환불 시도 건수 | 2 |
| 환불 결과 | 막힘 2 |
| 바뀐 주문 수 | 20 |
| 사고 뒤 정상 변경 주문 | 실행마다 다름(make capstone PART=answer) |
| trace_id 셋 | cap-3-001, cap-4-001, cap-5-001 |

## 단계별 표(막힘 · 탐지만 · 놓침)

| 단계 | 결과 | 근거(이벤트·로그 이름, ID만) | 판단 |
|---|---|---|---|
| 1 시크릿 유출 | 탐지만 | 도구 이벤트 secret.found.repository(repo:daon/ops-scripts, 규칙 daon-config-plain-credential), Gitea 커밋 시각 | 사실 |
| 2 새 곳 로그인 | 탐지만 | 로그인 기록(ums_admin_login_log): 평소 10.20.0.15·21시대 → 다른 IP·새벽. SECURITY 줄·경보 없음 | 사실(같은 사람인지는 모름) |
| 3 대량 조회 | 탐지만 | 감사 로그 order.list count 100 × 3, data.read.bulk, [다온 경보] 메일(8-4) | 사실 |
| 4 배송지 바꾸기 | 놓침 | 감사 로그 change.order.receiver 20줄(target order:<ID>)만. 경보 규칙 없음 | 사실 |
| 5 고액 환불 시도 | 막힘 | approval.denied.over_remaining(cap-5-001) · approval.pending.second_approver(cap-5-002), 결제사 환불 0건 | 사실 |
| 6 권한 올리기 시도 | 막힘 | 403(cap-6-001) · auth.denied.disabled_account(admin:daon_ops) | 사실 |

## 노출 범위(ID와 건수로만)

- 주문 목록 300건이 응답으로 나갔다. 화면 마스킹(5-4)으로 연락처는 가린 값이었다 → 「주문 300건(번호·상품·가린 연락처)」.
- 받는 사람 칸이 바뀐 주문 20건(감사 로그 target). 그 가운데 1건은 사고 뒤 고객 요청으로 다시 바뀌었다(admin:admin, 사유 CS-0415).
- 환불·출금은 0건. 권한은 바뀌지 않았다.
