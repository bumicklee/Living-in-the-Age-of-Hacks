# data.read.bulk

- 심각도: P2 · 담당: 보안 당번(가짜: security@daon.example) · 기준 시간: 확인 4시간(업무 시간) · 첫 조치 1일
- 뜻: 한 관리자 계정이 10분 동안 개인정보를 기준(랩 30건)보다 많이 조회했다(8-3 AUDIT 합계, rules/bulk.yaml).

## 1. 먼저 볼 질의

```
{stream="audit"} | json | actor="<메일의 actor>"
```
- 어떤 action(customer.list·order.list …)을 몇 건(count)씩, 어떤 사유(reason)로 봤는지. 사유가 비어 있으면 그 사람에게 먼저 묻는다.
- 업무 시간 밖인지, 평소와 다른 IP인지(같은 trace_id의 앱 로그 줄).

## 2. 첫 조치

- 본인에게 업무 사유 확인. 확인이 안 되면 그 관리자 계정 잠그기와 토큰 폐기(관리자 로그아웃).
- 되돌리기: 사유가 확인되면 계정 잠금을 푼다. 기준값이 업무량보다 낮으면 기준을 올리는 변경을 남긴다(누가·언제·왜).

## 3. 누구에게 알리나

- 보안 당번(security@daon.example), 그 관리자의 팀장(가짜: lead@daon.example). 외부 유출이 의심되면 14-4·14-6.
