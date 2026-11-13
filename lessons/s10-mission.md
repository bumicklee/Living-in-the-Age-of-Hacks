# 10-미션 멱등·웹훅 서명·예산을 secure-kit 부품으로
⏱ 약 20분(혼자 풀기) · 영상 없이 이 노트로 진행합니다 · 시험 없음(15장 secure-kit 정리 때 함께 확인)

10장에서 만든 막기 세 가지는 지금 기능 하나씩에 붙어 있습니다. 다음 기능(환불 웹훅, 포인트 충전, 이메일 인증)에도 같은 규칙을 한 줄로 붙일 수 있게 공용 부품으로 뽑습니다.

```bash
make goto L=10-mission
```

## 1. `@Idempotent` (10-2·10-4)
- `oms_payment_event`의 「(외부 ID, 이벤트) 유니크 INSERT → 중복 키면 처리 없이 200」을 애너테이션 하나로: `@Idempotent(key = "#paymentId + ':' + #event")`.
- 저장소는 DB 유니크 표(Redis만으로 하지 않는다: 만료·장애·DB 밖).
- 붙여 볼 곳: `DaonPaymentService.apply`, 환불 웹훅(가상).

## 2. `WebhookVerifier` (10-4)
- `DaonWebhookVerifier`를 mall-security로 옮기고 비밀값 경로(OpenBao KV)·헤더 이름·알고리즘을 설정으로.
- 지킬 것: 받은 바이트 그대로, 상수 시간 비교, 비밀을 못 읽으면 받지 않음(fail-closed), 서명이 중복 검사보다 먼저.
- 시각(`ts`)이 5분보다 오래된 웹훅을 거절하는 재전송 창을 더해 봅니다.

## 3. `@Budget` (10-6·10-7)
- `DaonCheckoutLimiter`·`DaonSmsBudget`의 공통 모양: 축(계정·IP·번호·카드 지문)마다 Redis 카운터 + 전체 상한 + 멈춤 키 + 이벤트.
- `@Budget(name = "sms", per = {PHONE, IP}, global = "20/1h", onTrip = "budget.tripped.global")`처럼 설정만 바꿔 붙입니다.
- 붙여 볼 곳: 비밀번호 재설정 메일(4-5), 이미지 가져오기(2-5).

## 확인(손으로)
```bash
make check L=10-4 && make check L=10-6 && make check L=10-7   # 부품으로 바꾼 뒤에도 그린
make switch K=all V=on
```

## 더 해 볼 것
- Express: `diffs/s10-04`·`s10-07`의 같은 규칙을 `lib/daon/` 아래 함수 두 개(`idempotent()`, `budget()`)로.
- 10-5 대사에 「가격 대사」(10-8 사후 점검 SQL)를 세 번째 대사로 넣어 봅니다.
