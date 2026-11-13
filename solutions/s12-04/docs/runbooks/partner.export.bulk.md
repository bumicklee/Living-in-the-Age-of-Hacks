# partner.export.bulk (파트너 대량 내보내기)

- 심각도: P2(폐기된 앱의 토큰이 다시 쓰이면 partner.denied.revoked P1)   · 담당: 보안 당번 + 대장의 그 앱 담당자   · 기준 시간: 확인 15분 · 첫 조치 1시간

## 1. 먼저 볼 질의

```
# 어느 파트너가 얼마나(10분 합계)
sum by (actor) (sum_over_time({stream="security", event="partner.export"} | json actor="actor", count="count" | unwrap count [10m]))
# 그 파트너의 다른 신호: 범위 밖 호출·바인딩 불일치·폐기 뒤 사용
{stream="security"} | json | actor="partner:<앱>" | event=~"partner.denied.*|token.denied.binding_mismatch"
```

```bash
make partner-audit              # 대장: 담당자·범위·마지막 사용·비밀값 회전일. 담당자에게 「지금 이 내보내기가 당신 쪽 작업인가」를 묻는다
```

## 2. 첫 조치

- 담당자가 모르는 내보내기이거나 15분 안에 닿지 않으면 **앱 단위 일괄 폐기**: `make partner-revoke APP=<앱>`
  (관리자 앱 `POST /daon/partner/apps/<앱>/revoke`: 상태 revoked · 비밀값 둘 다 거부 · 발급한 토큰 jti를 폐기 목록에. 한 번에 끝난다)
- 비밀값만 바꾸면 안 된다: 이미 발급한 토큰이 수명(15분) 동안 계속 쓰인다. 위 명령은 토큰까지 끊는다.
- 되돌리기(담당자 확인 뒤): 새 비밀값을 발급해 담당자에게 안전한 길로 전하고 상태를 active로(랩: `make partner-reset`).
- 폐기 뒤 `partner.denied.revoked`가 나오면 도난 토큰을 누군가 쓰고 있다는 신호다 → 14장 사고 대응으로.

## 3. 누구에게 알리나

- 보안 당번 security@daon.example(가짜), 대장의 그 앱 담당자(가짜 주소), 개인정보가 나갔을 수 있으면 개인정보 담당 privacy@daon.example(가짜)
