# access.denied.not_owner

- 심각도: P2 · 담당: 고객 서비스 개발 당번(가짜: oncall-app@daon.example) · 기준 시간: 확인 4시간(업무 시간) · 첫 조치 1일
- 뜻: 같은 회원이 10분 안에 남의(또는 없는) 주문을 5번 넘게 열려다 막혔다. 주문 번호를 훑는 모습이다(2-2).

## 1. 먼저 볼 질의

```
{event="access.denied.not_owner"} | json | actor="<메일의 actor>"
```
- 몇 개의 주문 번호를 훑었는지(target), 어느 IP·User-Agent인지(ip·ua) 본다.
- 막기 전에 들어온 흔적: 같은 actor가 같은 target을 성공적으로 연 적이 있는지 감사 로그·trace_id로 이어 본다.
  `{stream="audit"} | json | target="<order:번호>"` (관리자 조회만 남는다. 고객 조회는 앱 로그의 trace_id로)

## 2. 첫 조치

- 그 회원 토큰 폐기(4-8 로그아웃 폐기 목록) · 같은 IP 속도 제한 확인(4-3).
- 되돌리기: 정상 사용자로 확인되면 폐기 목록에서 지울 필요는 없다(다시 로그인하면 된다).

## 3. 누구에게 알리나

- 고객 서비스 개발 당번(oncall-app@daon.example), 남의 주문이 실제로 열렸다면 개인정보 담당(privacy@daon.example)과 14-6 판단표.
