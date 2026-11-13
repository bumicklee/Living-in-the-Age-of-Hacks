# 13-미션 승인 부품 `@RequiresApproval` 뽑기
⏱ 약 25분(혼자 풀기) · 영상 없이 이 노트로 진행합니다 · 시험 없음(15장 secure-kit 정리 때 함께 확인)

13-3·13-4에서 손으로 쓴 「요청만 하는 클라이언트 + 승인 토큰을 확인하는 실행기」를 secure-kit 부품으로 뽑습니다.

```bash
make goto L=13-mission
```

## 1. 부품 모양
```java
@RequiresApproval(policy = "refund.high-value")      // 승인 서비스 정책 이름
public void refund(Long returnId, BigDecimal requestedAmount) { ... }
```
- 붙인 메서드는 바로 실행되지 않고 승인 서비스에 「요청」만 갑니다(`DaonApprovalClient`). 실행은 승인 토큰 확인(`DaonApprovalToken`·`DaonRefundExecutor`의 ⓐ~ⓓ) 뒤에만.
- Express는 같은 일을 미들웨어로: `kit.requiresApproval('refund.high-value')`.

## 2. 두 기능에 한 줄로 붙여 봅니다
- **관리자 수동 포인트 지급(가상):** 원본 mall에 관리자 포인트 지급 화면이 있는지 먼저 찾아봅니다. 없으면 교육용으로 하나 만듭니다(`/daon/points/grant`). 고액(예: 10,000점 이상)은 승인 서비스로.
- **정산 계좌 변경 직후 정산(가상):** 계좌를 바꾼 뒤 정해진 시간 안의 정산은 「대상 계좌 규칙」으로 두 번째 사람·지연 실행을 거치게 합니다.
- 정책 이름을 `policy.yml`에 더하고, 같은 SECURITY 이벤트(`approval.denied.*`·`refund.denied.no_approval`과 같은 모양)가 남는지 봅니다.

## 3. 생각할 것
- 승인 서비스 하나에 환불·권한 상승·포인트·정산이 모두 모이면 그것이 새 단일 장애점이 됩니다. 승인 서비스의 운영 접근(13-6)과 백업(14장)을 함께 생각합니다.
- 서비스 사이 인증(관리자 앱 ↔ 승인 서비스)을 12-3의 mTLS로 붙이면 무엇이 달라지는지 그려 봅니다.

## 확인(손으로)
```bash
make check L=13-3 && make check L=13-4 && make check L=13-6   # 부품을 다시 쓴 뒤에도 그린
make switch K=all V=on
```
