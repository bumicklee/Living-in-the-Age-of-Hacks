# express-shop (15-4 수업)

회사 서비스를 흉내 낸 작은 Express 앱입니다. 자기 로그인(토큰 `shop-<무작위>`)을 쓰고, 15-4 수업에서 secure-kit 부품 셋만 붙입니다. 값은 모두 가짜입니다.

| 부품 | 붙이기 전 | 붙인 뒤 |
|---|---|---|
| `kit.errorResponse()` | 500 본문에 예외 이름과 SQL 조각이 나갑니다 | `{code, message, traceId}`만 나갑니다 |
| `kit.rateLimit('login')` | 비밀번호를 6번 틀려도 계속 401입니다 | 6번째 요청은 429입니다 |
| `kit.audited('member.read')` | 감사 기록이 없습니다 | 누가 어느 회원을 왜 봤는지 남습니다 |

바뀌는 파일은 `package.json`(의존성 한 줄)과 `app.js` 두 개뿐입니다. `shop.js`(가게 로직)와 `server.js`(기동)는 그대로입니다.

```bash
npm install --no-audit --no-fund
PORT=18184 node server.js
```

- 계정: `shop-admin` / `shop-pass-1!` (가짜)
- 주소: `GET /api/health`, `POST /api/login`, `GET /api/orders/:id`, `GET /api/admin/members/:id`, `POST /api/_reset`
