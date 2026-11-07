# 가짜 외부 서비스(스텁, A11)

다온마켓이 「밖」에 기대는 서비스를 이 PC 안에 흉내 낸다. 진짜 결제·문자·택배·메일은 한 건도 나가지 않는다.
모두 `infra/compose.lab.yml`에 있고, `make up`(어느 스택이든)과 함께 뜬다. PC 쪽 포트는 127.0.0.1에만 열린다.

| 서비스 | 컨테이너 안 주소 | PC에서 | 쓰는 수업 |
|---|---|---|---|
| 가짜 결제사 `payment-stub` | `http://payment-stub:8000` | http://127.0.0.1:9101 | 10장(웹훅 멱등·서명) |
| 가짜 문자 `sms-stub` | `http://sms-stub:8000` | http://127.0.0.1:9102 | 4장(인증번호·재설정) |
| 가짜 택배 `courier-stub` | `http://courier-stub:8000` | http://127.0.0.1:9103 | 9장(외부 호출 장애 격리) |
| 메일 `mailpit` | SMTP `mailpit:1025` | 받은 메일 http://127.0.0.1:8025 · SMTP 127.0.0.1:1025 | 4장(재설정 링크), 8·12장(경보 메일) |
| 이미지 공급사 `partner-cdn` | `img.partner-cdn.example`(랩의 「인터넷」 망) | 열지 않음 | 2-5(SSRF·이그레스) |

포트는 `infra/.env`의 `DAON_PAYMENT_PORT`·`DAON_SMS_PORT`·`DAON_COURIER_PORT`·`DAON_MAILPIT_PORT`·`DAON_SMTP_PORT`로 바꾼다.
스텁은 표준 라이브러리만 쓰는 파이썬 파일 하나씩이다(`stubs/<이름>/server.py`, 이미지 `python:3.14.10-slim-trixie`, 읽기 전용·nobody 사용자).
값은 메모리에만 있어서 컨테이너를 다시 띄우면 비워진다.

## 가짜 결제사 (`stubs/payment`)

```bash
curl -s -X POST 127.0.0.1:9101/v1/payments -H 'Content-Type: application/json' \
  -d '{"order_sn":"DAON202611010001","amount":39000,"callback_url":"http://mall-portal:8085/pay/webhook"}'
# → {"payment_id":"PAY000001","status":"approved",…}  그리고 callback_url로 웹훅을 보낸다
curl -s -X POST 127.0.0.1:9101/v1/payments/PAY000001/resend   # 같은 웹훅을 한 번 더(중복 웹훅 재연)
curl -s 127.0.0.1:9101/v1/webhooks                              # 보낸 웹훅과 받은 쪽 응답 코드
```

- 웹훅 본문: `{"event":"payment.approved","payment_id":…,"order_sn":…,"amount":…,"ts":…}`
- 서명 헤더 `X-Daon-Signature` = hex(HMAC-SHA256(비밀값, 본문 바이트)). 비밀값은 compose의 `PAYMENT_WEBHOOK_SECRET`(랩 전용 가짜 값 `lab-fake-webhook-secret`).
- 지금 mall-portal에는 웹훅을 받는 곳이 없다. 고객 웹의 「가짜 결제하기」는 원본 그대로 `/order/paySuccess`를 부른다. 웹훅 수신은 10장에서 만든다.

## 가짜 문자 (`stubs/sms`)

```bash
curl -s -X POST 127.0.0.1:9102/v1/messages -H 'Content-Type: application/json' -d '{"to":"010-0000-0101","text":"인증번호 123456"}'
curl -s '127.0.0.1:9102/v1/messages?to=010-0000-0101'
```

받는 번호는 `010-0000-xxxx`만 받는다(그 밖은 400). 실제로 보내지 않고 목록에 쌓기만 한다.

## 가짜 택배 (`stubs/courier`)

```bash
curl -s 127.0.0.1:9103/v1/tracking/DT0000009004
```

시드의 송장 번호(`DT` + 주문 id 10자리, 발송·완료 주문)에 늘 같은 답을 준다. 끝자리로 단계를 정한다(0~2 접수, 3~4 집하, 5~6 배송 중, 7~9 배송 완료).

## 메일 (Mailpit `axllent/mailpit:v1.32.0`)

앱은 SMTP `mailpit:1025`(인증 없음)로 보낸다. 받은 메일은 http://127.0.0.1:8025 에서 본다. 최대 500통을 남긴다.

## 지키는 것

- 스텁은 랩 안의 가짜 값만 다룬다. 실제 카드 번호·전화번호·주소를 넣지 않는다.
- 스텁에 일부러 약점을 두지 않는다(약점은 다온마켓 쪽에 있다). 수업에서 스텁을 바꿀 일이 생기면 이 문서에 적는다.
