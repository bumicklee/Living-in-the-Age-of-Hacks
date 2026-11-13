# 가짜 외부 서비스(스텁, A11)

다온마켓이 「밖」에 기대는 서비스를 이 PC 안에 흉내 낸다. 진짜 결제·문자·택배·메일은 한 건도 나가지 않는다.
모두 `infra/compose.lab.yml`에 있고, `make up`(어느 스택이든)과 함께 뜬다. PC 쪽 포트는 127.0.0.1에만 열린다.

| 서비스 | 컨테이너 안 주소 | PC에서 | 쓰는 수업 |
|---|---|---|---|
| 가짜 결제사 `payment-stub` | `http://payment-stub:8000` | http://127.0.0.1:9101 | 10장(웹훅 멱등·서명), 13장(환불 기록) |
| 가짜 문자 `sms-stub` | `http://sms-stub:8000` | http://127.0.0.1:9102 | 4장(인증번호·재설정) |
| 가짜 택배 `courier-stub` | `http://courier-stub:8000` | http://127.0.0.1:9103 | 9장(외부 호출 장애 격리) |
| 메일 `mailpit` | SMTP `mailpit:1025` | 받은 메일 http://127.0.0.1:8025 · SMTP 127.0.0.1:1025 | 4장(재설정 링크), 8·12장(경보 메일), 13-4(승인 확인 번호) |
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
- 9장까지 mall-portal에는 웹훅을 받는 곳이 없다. 고객 웹의 「가짜 결제하기」는 원본 그대로 `/order/paySuccess`를 부른다. 웹훅 수신(`POST /pay/webhook`)과 결제 시작(`POST /pay/start`)은 10-4에서 만든다.
- 비밀값은 `DAON_PAYMENT_WEBHOOK_SECRET`(없으면 `lab-fake-webhook-secret`). 10-4부터 앱은 같은 값을 OpenBao KV `secret/daon/payment-webhook`에서 읽는다(`infra/openbao/init.sh`).

가짜 테스트 카드 토큰(10-6, 2026-11-10 더함, 결정 #14 「거절 모드」):

- `card_token`(선택)은 `tok_test_` + 숫자 4~16자리만 받는다(그 밖은 400). 실제 카드 번호 모양은 받지 않는다.
- 끝자리가 `2` 또는 `4`면 승인(201 + 웹훅), 그 밖은 거절(402 `{"status":"declined"}`, 웹훅 없음). 토큰이 없으면 늘 승인(9장 결제 요청은 토큰을 보내지 않는다).
- 응답의 `card_fingerprint`는 `fp_` + 토큰 SHA-256 앞 16자(가짜 카드 지문). 앱은 카드 대신 이 지문으로 센다(10-6의 카드 축).
- 거절은 결제사의 정상 동작이다. 「스텁에 일부러 약점을 두지 않는다」와 부딪히지 않는다.

```bash
curl -s -X POST 127.0.0.1:9101/v1/payments -H 'Content-Type: application/json' \
  -d '{"order_sn":"DAON202611010001","amount":39000,"card_token":"tok_test_0001"}'   # → 402 declined
```

결제 확인(14장, 2026-11-10 더함): `GET /v1/payments/by-order/<주문 번호>` → 그 주문의 마지막 결제 상태(`APPROVED`·`DECLINED`, 없으면 404 `NONE`). 고객 앱 `GET /pay/status`가 부른다. 느림 모드(`make stub-fault S=pay MODE=slow MS=3000`)는 결제 요청과 함께 이 확인에도 걸린다(14-3 재연: 오류 없이 느리기만 한 결제사). 느림은 장애를 흉내 내는 스위치이고 스텁의 약점이 아니다.

가짜 환불(13장, 2026-11-10 더함): 기록만 남긴다. 금액을 판단하는 것은 다온마켓 쪽(승인 서비스·실행기)이다. 결제사 쪽 상한은 일부러 두지 않았다.

```bash
curl -s -X POST 127.0.0.1:9101/v1/refunds -H 'Content-Type: application/json' -d '{"order_sn":"DAON202611010001","amount":10000,"return_id":31}'
curl -s '127.0.0.1:9101/v1/refunds?return_id=31'      # 그 반품 신청의 환불(JSON)
open http://127.0.0.1:9101/_stub/refunds                # 사람이 보는 환불 목록(13-3 화면 캡처)
```

## 가짜 문자 (`stubs/sms`)

```bash
curl -s -X POST 127.0.0.1:9102/v1/messages -H 'Content-Type: application/json' -d '{"to":"010-0000-0101","text":"인증번호 123456"}'
curl -s '127.0.0.1:9102/v1/messages?to=010-0000-0101'
```

받는 번호는 `010-0000-xxxx`만 받는다(그 밖은 400). 실제로 보내지 않고 목록에 쌓기만 한다.
10-7(문자 예산)은 이 스텁을 바꾸지 않는다. 「국가 제한」은 앱이 국내 휴대전화 형식만 스텁에 보내는 장면으로 보인다. LLM 스텁은 만들지 않는다(결정 #14, 그림 설명).

본인 확인(문자 사업자의 「인증 번호 확인 API」 흉내, 9-2):

```bash
curl -s -X POST 127.0.0.1:9102/v1/verify/start -H 'Content-Type: application/json' -d '{"to":"010-0000-0101"}'      # 여섯 자리 번호를 문자 목록에
curl -s -X POST 127.0.0.1:9102/v1/verify/check -H 'Content-Type: application/json' -d '{"to":"010-0000-0101","code":"123456"}'   # {"valid":…}
```

## 가짜 택배 (`stubs/courier`)

```bash
curl -s 127.0.0.1:9103/v1/tracking/DT0000009004
```

시드의 송장 번호(`DT` + 주문 id 10자리, 발송·완료 주문)에 늘 같은 답을 준다. 끝자리로 단계를 정한다(0~2 접수, 3~4 집하, 5~6 배송 중, 7~9 배송 완료).

## 가짜 파트너 앱 (`stubs/partner`, 12장)

다른 스텁과 반대 방향이다: 다온마켓이 부르는 「밖」이 아니라 **다온마켓 파트너 API를 부르는 「밖」**(가상 「다온 배송 파트너」)이다.
그래서 컨테이너가 아니라 PC에서 도는 파이썬 파일 하나(`partner_app.py`, 표준 라이브러리만)이고, 127.0.0.1만 부른다.

```bash
make partner-app                         # courier-01: 배정 주문을 쪽마다 받아 건수만 찍는다
make partner-app APP=analytics-01 DO=export
python stubs/partner/partner_app.py --base http://127.0.0.1:8085 --app courier-01 orders   # 12-2 시작(A9 전)
```

- 다온마켓 쪽 상태에 맞춰 스스로 고른다: 발급 주소가 없으면 고정 키(`X-Partner-Key`, 12-2 시작), 있으면 15분 토큰(만료면 한 번 다시 받음),
  파트너 입구가 https면 자기 인증서로 mTLS(12-3, `make partner-certs`가 `.daon/partner-pki/`에 가져온다. git에 넣지 않음).
- 가짜 파트너 셋(시드 `DaonPartnerSchema`): `courier-01`(배송, 배정 81건), `analytics-01`(판매 분석, 내보내기 범위, 21건), `legacy-01`(계약 끝, 120일 안 씀).
  비밀값 `lab-fake-partner-secret-<앱>-v1`, 고정 키 `lab-fake-partner-key-<앱>`(모두 랩 전용 가짜 값).
- 토큰·비밀값·받는 사람 정보는 찍지 않는다(건수와 주문 번호만).

## 메일 (Mailpit `axllent/mailpit:v1.32.0`)

앱은 SMTP `mailpit:1025`(인증 없음)로 보낸다. 받은 메일은 http://127.0.0.1:8025 에서 본다. 최대 500통을 남긴다.

## 장애 넣기(랩 도구, 9-2·9-4)

문자(9102)·결제사(9101) 스텁은 `POST /_lab/fault {"mode":"ok"|"500"|"slow","ms":3000}`로 일부러 실패하거나 늦게 답한다(늦추기는 최대 5초). 손으로는 `make stub-fault S=sms|pay MODE=500|slow|ok [MS=3000]`.
결제사 스텁은 받은 결제 요청을 센다: `GET /_lab/stats`(시각·`X-Trace-Id`·`Idempotency-Key`·응답 코드), `POST /_lab/reset`. 늦게 답하는 모드에서도 결제는 끝까지 처리한다(부른 쪽이 먼저 끊어도 결제사는 처리했을 수 있다: 10-4 멱등 키의 이유).
장애 모드는 메모리에만 있다. 켠 채 두면 `make check`가 알려 준다. 컨테이너를 다시 띄우면 정상으로 돌아간다.

## 지키는 것

- 스텁은 랩 안의 가짜 값만 다룬다. 실제 카드 번호·전화번호·주소를 넣지 않는다.
- 스텁에 일부러 약점을 두지 않는다(약점은 다온마켓 쪽에 있다). 수업에서 스텁을 바꿀 일이 생기면 이 문서에 적는다.
