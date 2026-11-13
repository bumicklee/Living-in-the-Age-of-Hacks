# @secure-kit/express

막을 내용 하나에 부품 하나씩 있는 Express 4/5용 보안 미들웨어 24개입니다. 필요한 부품만 골라서 끼우면 됩니다. 라이선스는 MIT입니다.

- 수업에서 손으로 고친 코드를 정리해 만든 부품입니다. mall·EverShop 코드는 가져오지 않고 새로 썼습니다.
- 런타임 의존성이 없습니다(`node:crypto`·`node:dns`·`node:http`·전역 `fetch`만 씁니다). express도 가져오지 않습니다.
- 소스는 TypeScript가 아니라 **빌드 없이 바로 도는 순수 JavaScript(ES 모듈)**이고, 타입은 손으로 쓴 `index.d.ts`가 맡습니다. 처음 설계(TypeScript)를 이렇게 바꿨습니다.
- Node 20 이상에서 돕니다.

## 설치

아직 npm 레지스트리에 올리지 않았습니다. 저장소 안의 경로로 받습니다.

```jsonc
// 내 서비스의 package.json
"dependencies": {
  "@secure-kit/express": "file:../path/to/packages/secure-kit-express"
}
```

npm workspace를 쓰는 저장소라면 `workspaces`에 `packages/secure-kit-express`를 넣어도 됩니다.

## 넣는 모습

```js
import { secureKit } from '@secure-kit/express';
const kit = secureKit(config);            // 설정 키는 Spring application.yml 의 secure-kit.* 와 같습니다

app.disable('x-powered-by');
app.use(kit.headers(), kit.hostAllowlist(), kit.noStoreAuthenticated(), kit.bodyLimit());
router.post('/login', kit.rateLimit('login'), login);
router.post('/orders/:id/refund',
  kit.idempotent({ header: 'Idempotency-Key', ttl: '24h' }),
  kit.requiresApproval('refund.high-value'),
  kit.audited('order.refund'),
  refund);
app.use(kit.notFound());
app.use(kit.errorResponse());             // 오류 처리기(인자 4개)라서 맨 끝에 둡니다
```

`errorResponse()`는 Express 오류 처리기라서 **모든 라우트 뒤**에 둬야 합니다. 앞에 두면 라우트에서 난 예외를 받지 못합니다.

### 설정은 Spring과 같은 키를 씁니다

`application.yml`의 `secure-kit:` 아래를 그대로 객체로 옮깁니다. kebab-case 키는 문자열로 그대로 씁니다. `{ 'secure-kit': {...} }`처럼 감싸서 넘겨도 되고, 안쪽만 넘겨도 됩니다.

```js
const config = {
  'secure-kit': {
    'host-allowlist': ['shop.example'],
    cors: { 'allowed-origins': ['https://shop.example'] },
    'rate-limit': { login: { 'per-account': '5/10m', 'per-ip': '50/10m' } },
    token: { secret: process.env.KIT_TOKEN_SECRET, kid: 'k1', audience: 'kit', ttl: '15m' },
    approval: { policies: { 'refund-high-value': { threshold: 300000 } } }
  }
};
```

## 넣는 법은 세 가지입니다

| 넣는 법 | 뜻 | 부품 |
|---|---|---|
| ① 설정만 켜기 | `app.use(kit.x())` 한 번 + 설정 | headers, hostAllowlist, token, startupCheck, errorResponse, logger, bodyLimit, resilientFetch, noStoreAuthenticated |
| ② 미들웨어 한 줄 | 위험한 라우트 앞에 한 줄 | rateLimit, requireStepUp, fieldCrypto(seal/open), audited, failClosed, idempotent, webhook, budget, partnerScope, requiresApproval |
| ③ 바꿔 쓰기 | 쓰던 함수 대신 안전한 부품 | safeFetch(fetch 대신), safeRedirect(res.redirect 대신), upload, passwordPolicy, constantTimeLogin, resetLink, safeRegex(new RegExp 대신) |

## 부품 24개

| # | 키 | Express | 막는 것 |
|---|---|---|---|
| 1 | safe-http-client | `kit.safeFetch()` | 내부망으로 나가는 요청(SSRF). 이름을 풀어 모든 주소를 확인하고, 확인한 주소로만 붙고, 리다이렉트는 따라가지 않습니다 |
| 2 | host-allowlist | `kit.hostAllowlist()` | 낯선 Host(DNS 리바인딩). 포트는 보지 않습니다 |
| 3 | safe-redirect | `kit.safeRedirect()` | 열린 리다이렉트 |
| 4 | upload | `kit.upload()` | 내용이 이미지가 아닌 파일, 큰 파일. 저장 이름은 무작위로 정합니다 |
| 5 | headers | `kit.headers()` | 보안 헤더·CSP·CORS 허용 출처 |
| 6 | rate-limit | `kit.rateLimit('login')` | 크리덴셜 스터핑. 계정별·IP별 실패 횟수를 셉니다 |
| 7 | password-policy | `kit.passwordPolicy()` | 짧은 비밀번호와 유출된 비밀번호 |
| 8 | constant-time-login | `kit.constantTimeLogin()` | 응답 시간으로 회원인지 알아내기 |
| 9 | reset-link | `kit.resetLink()` | 재설정 링크 오염(Host 머리), 토큰 재사용 |
| 10 | step-up | `kit.requireStepUp()` | 탈취한 세션으로 하는 고위험 변경 |
| 11 | token | `kit.token()` | alg 바꿔치기, 만료 없는 토큰, 로그아웃한 토큰 |
| 12 | field-crypto | `kit.fieldCrypto()` | DB 유출 시 개인정보 평문(AES-GCM과 검색용 해시) |
| 13 | startup-check | `kit.startupCheck()` | 기본값 비밀값으로 기동하는 것 |
| 14 | error-response | `kit.errorResponse()` | 오류 응답으로 새는 예외·SQL·스택 |
| 15 | audit | `kit.audited()` | 누가 무엇을 봤는지 모르는 상태 |
| 16 | log-masking | `kit.logger()` | 로그에 남는 전화번호·비밀번호·토큰 |
| 17 | limits | `kit.bodyLimit()`·`kit.safeRegex()` | 큰 본문, 되돌아가기가 폭주하는 정규식 |
| 18 | fail-closed | `kit.failClosed()` | 의존성이 죽었을 때 일단 통과시키는 것 |
| 19 | resilient-client | `kit.resilientFetch()` | 재시도 폭주(재시도 예산과 서킷) |
| 20 | idempotency | `kit.idempotent()`·`kit.webhook()` | 중복 요청, 위조·재전송한 웹훅 |
| 21 | budget | `kit.budget('sms')` | 문자 폭탄과 비용 폭주 |
| 22 | no-store | `kit.noStoreAuthenticated()` | 인증 응답이 캐시에 남는 것 |
| 23 | partner-scope | `kit.partnerScope()` | 파트너 토큰의 범위 밖 호출 |
| 24 | approval | `kit.requiresApproval()` | 혼자 끝내는 고액 작업 |

HTTP로 확인하는 계약은 `packages/contract-tests/CONTRACT.md`에 있고, 시험은 `packages/contract-tests/`에 있습니다. 회사 서비스에 부품을 붙인 뒤에도 주소만 바꿔서 같은 시험을 돌릴 수 있습니다.

## 공통 약속

- **막으면 남깁니다.** 부품이 요청을 막으면 보안 이벤트를 한 줄 남깁니다. 모양은 `WARN SECURITY {"seq","event","part","level","actor","target","reason","ts","trace_id"}`이고, 비밀번호·토큰·전화번호 같은 값은 넣지 않습니다.
- **실패하면 막습니다.** 저장소 오류처럼 부품 안에서 문제가 생기면 기본으로 막습니다. 풀려면 그 부품 설정에 `'fail-open': true`를 직접 적어야 합니다.
- **행위자(actor)**: 이 kit의 `token()`을 쓰면 kit이 알아서 채웁니다. 다른 인증을 쓰는 앱은 인증 미들웨어에서 `req.secureKitActor = 'admin:아이디'`를 넣습니다.
- **추적 번호**: 요청의 `X-Trace-Id`가 안전한 모양이면 그 값을 쓰고, 아니면 새로 만듭니다. 응답 머리와 오류 본문의 `traceId`, 이벤트와 감사 기록의 `trace_id`가 모두 같은 값입니다.

## 저장소를 Redis로 바꾸기

기본 저장소는 프로세스 메모리(`MemoryStore`)입니다. 서버가 두 대 이상이면 속도 제한·멱등·폐기 목록·승인을 한곳에서 세야 하므로 같은 모양의 구현을 넘깁니다. 이 패키지는 Redis 의존성을 넣지 않았습니다.

```js
// 예: ioredis 로 만든 저장소(내 서비스 쪽 코드)
const store = {
  get: async (k) => JSON.parse((await redis.get(k)) ?? 'null') ?? undefined,
  set: async (k, v, ttl) => { await (ttl ? redis.set(k, JSON.stringify(v), 'PX', ttl) : redis.set(k, JSON.stringify(v))); },
  setIfAbsent: async (k, v, ttl) => (await redis.set(k, JSON.stringify(v), 'PX', ttl, 'NX')) === 'OK',
  incr: async (k, ttl) => {
    const count = await redis.incr(k);
    if (count === 1) await redis.pexpire(k, ttl);
    return { count, ttlMs: Math.max(0, await redis.pttl(k)) };
  },
  del: async (k) => { await redis.del(k); },
  clear: async () => { /* 운영에서는 비워 두세요(랩 /_kit/reset 전용) */ }
};
const kit = secureKit(config, { store });
```

Redis 연결이 끊기면 부품은 기본으로 503을 돌려줍니다(fail-closed). 서킷(resilientFetch) 상태는 서버마다 따로 셉니다.

## 부품으로 만들지 않은 것

아래 항목은 도메인 코드와 DB 설계로 막아야 합니다. 수업에서 패턴과 점검 방법을 따로 다룹니다.

- **남의 주문 조회(IDOR)**: 소유권 조건은 조회 쿼리 안에 들어가야 합니다. 부품이 대신해 줄 수 없습니다.
- **대량 할당**: 요청 전용 DTO, 즉 받는 칸만 고르는 코드를 따로 둡니다.
- **SQL 인젝션**: 매개변수 쿼리를 씁니다.
- **경쟁 조건**: 조건부 UPDATE와 유니크 제약을 씁니다. `idempotent()`는 같은 키로 들어온 중복 요청만 막고, 동시성은 DB가 막습니다.
- **원장 대사, 아키텍처**(망 분리·백업 등): compose 레시피로 다룹니다.

## 영상용 `enabled` 스위치

부품 설정에 `enabled: false`를 넣으면 그 부품은 그냥 통과합니다. 수업 영상에서 고치기 전과 후를 나란히 보여 줄 때만 쓰는 스위치입니다. 회사 서비스에서는 쓰지 마세요. 부품을 끄고 싶으면 그 줄을 지우면 됩니다.

## 랩 전용 `/_kit/*`

`kit.lab()`은 `secure-kit.lab.expose: true`일 때만 `/_kit/reset`·`/_kit/events`·`/_kit/audit` 같은 경로에 답합니다. 기본값은 꺼짐(false)이고, 회사 서비스에서는 켜지 않습니다. 계약 시험이 상태를 지우고 이벤트를 읽는 통로입니다.

## 라이선스

MIT. `LICENSE`를 보세요.
