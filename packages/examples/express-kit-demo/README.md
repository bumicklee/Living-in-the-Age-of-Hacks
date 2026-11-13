# express-kit-demo

`@secure-kit/express` 부품 24개를 모두 끼운 작은 Express 앱입니다. Spring 예제(`spring-kit-demo`)와 주소·응답·이벤트 이름이 같아서, 계약 시험(`packages/contract-tests/test_parts.py`)을 두 앱에 똑같이 돌릴 수 있습니다. 값은 모두 랩 전용 가짜 값입니다.

```bash
npm install --no-audit --no-fund
PORT=18182 node server.js        # 컨테이너 안에서는 0.0.0.0, 호스트에는 127.0.0.1:18182 로만 내보냅니다
```

- 설정: `config.js` (Spring 예제의 `application.yml`과 같은 키)
- 라우트: `app.js` (라우트마다 「얇은 처리 함수 + 부품 한 줄」)
- 데모 사용자: `kit-user` / `kit-pass-1!`, `kit-admin` / `kit-admin-pass-1!` (가짜)
- 랩 전용 `/_kit/*`는 `KIT_LAB_EXPOSE=false`로 끕니다.
