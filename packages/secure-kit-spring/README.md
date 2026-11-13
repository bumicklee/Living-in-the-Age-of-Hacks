# secure-kit (Spring)

막을 내용 하나에 부품 하나. 수업에서 손으로 먼저 고친 코드를 정리해, 다른 서비스에도 **설정·어노테이션·바꿔 쓰기**로 붙일 수 있게 만든 보안 부품 24개다.

- 라이선스: MIT(`LICENSE`). 포크한 원본(mall·EverShop) 코드는 가져오지 않고 새로 썼다.
- 최소 버전: Java 17 · Spring Boot 3.2 이상(3.5.14로 확인).
- `groupId io.github.securekit`은 **임시 이름**이다. Maven Central에는 아직 올리지 않았다(출시 뒤 정한다).
- 같은 계약을 Express 쪽(`packages/secure-kit-express`)도 지킨다. 설정 키가 같다.

## 설치(로컬)

```bash
mvn -B -f packages/secure-kit-spring/pom.xml install -DskipTests
```

내 서비스 `pom.xml`에 한 줄:

```xml
<dependency>
    <groupId>io.github.securekit</groupId>
    <artifactId>secure-kit-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

`spring-boot-starter-web`이 있는 서블릿 앱이어야 한다. 여러 대로 돌리면 `spring-boot-starter-data-redis`를 더하고 `secure-kit.store.type: redis`.

## 모듈

| 모듈 | 내용 | 의존성 |
|---|---|---|
| `secure-kit-core` | 보안 이벤트 형식·보관(`SecurityEvents`), 비밀번호 규칙, 느린 정규식, 이동 주소 규칙, 내부망 주소 판정, 가리기 규칙, HS256 토큰, 칸 암호화(AES-GCM + 검색용 해시), 기동 시크릿 규칙, 업로드 매직 바이트, 카운터 저장소(메모리) | JDK만 |
| `secure-kit-web` | 서블릿 필터·어노테이션·애스펙트: Host 허용 목록, 헤더·CORS, 오류 응답, 본문 상한, no-store, 토큰, 재인증, 파트너 범위, 승인, 멱등, 웹훅, 예산, 실패하면 막기, 속도 제한, 안전한 HTTP 클라이언트·재시도 예산 | Spring Web MVC, AOP |
| `secure-kit-redis` | `RedisCounterStore`(여러 대가 속도 제한·멱등·폐기 목록을 공유). Redis 오류는 기본 막기 | spring-data-redis(선택) |
| `secure-kit-audit` | `@Audited` 감사 기록, Logback 가리기 변환기 `MaskingConverter`, 랩 전용 최근 로그 | Logback |
| `secure-kit-spring-boot-starter` | 자동 설정(`secure-kit.*`), 기동 검사기, 랩 전용 `/_kit/*` | Spring Boot |

## 넣는 법 세 가지

**① 설정만 켜기** — `application.yml`

```yaml
secure-kit:
  host-allowlist: { enabled: true, hosts: [shop.example, localhost] }
  headers: { enabled: true }
  cors: { allowed-origins: [https://shop.example] }
  error-response: { enabled: true }
  limits: { enabled: true, max-body: 1MB, paths: { "[/upload]": 3MB } }
  cache-control: { enabled: true }          # 로그인한 응답은 no-store
  token: { enabled: true, secret: ${TOKEN_SECRET}, kid: k1, ttl: 15m, audience: kit }
  rate-limit:
    login: { per-account: 5/10m, per-ip: 50/10m }
  log-masking: { enabled: true, fields: [password, token, secret, card] }
  store: { type: redis, fail-open: false }  # 한 대면 지운다(메모리)
```

**② 어노테이션 한 줄**

```java
@PostMapping("/login")
@RateLimited("login")
public ResponseEntity<?> login(@RequestBody LoginRequest body) { ... }

@GetMapping("/admin/members/{id}")
@Audited(action = "member.read", resource = "member")
public MemberView read(@PathVariable long id) { ... }

@PostMapping("/orders/{id}/refund")
@Idempotent(header = "Idempotency-Key", ttl = "24h")
@RequiresApproval(policy = "refund-high-value")
public RefundResult refund(@RequestBody RefundRequest body) { ... }
```

그 밖에 `@RequireStepUp`, `@Budget`, `@FailClosed`, `@PartnerScope`, `@Encrypted`(칸에 붙인다).

**③ 안전한 부품으로 바꿔 쓰기** — 빈을 주입받아 기존 호출 대신 쓴다.

| 대신 | 이것 |
|---|---|
| `restTemplate.getForObject(url)` | `safeHttpClient.get(url)` |
| `response.sendRedirect(next)` | `safeRedirect.redirect(next)` |
| `file.transferTo(원래 이름)` | `uploadValidator.store(in)` |
| 길이 규칙만 보는 비밀번호 검사 | `passwordPolicy.check(pw)` |
| 없는 아이디면 바로 401 | `constantTimeLogin.matches(hashOrNull, pw)` |
| `"https://" + request.getHeader("Host") + "/reset?…"` | `resetLinkBuilder.issue(user, request)` |
| `Pattern.matches(re, input)` | `new SafeRegex(re, maxLength).matches(input)` |
| 재시도 무한 루프 | `resilientClients.create("pg").get(url)` |

## 공통 약속

- 오류 응답: `{"code": 상태, "message": "<한국어 일반 문구>", "traceId": "<추적 번호>"}`. 모든 응답에 `X-Trace-Id`.
- **막으면 남긴다**: 막을 때마다 `WARN SECURITY {"seq","event","part","level","actor","target","reason","ts","trace_id"}` 한 줄. 비밀번호·토큰·전화번호 원문은 넣지 않는다.
- **실패하면 막는다**: 부품 내부 오류(Redis 연결 실패 등)는 막는 쪽. 풀려면 `fail-open: true`를 명시한다.
- 자기 로그인을 쓰는 서비스는 요청 속성 `secure-kit.actor`에 `admin:아이디` 같은 값을 넣으면 감사 기록·이벤트가 그 값을 쓴다.
- 기본값: 설정 부품(①)은 꺼져 있고 켜야 돈다. 어노테이션 부품(②)은 붙인 곳에서만 돈다. 기동 검사기는 켜져 있다.

## 부품으로 만들지 않은 것

도메인 코드와 DB 설계로 막아야 하는 것은 부품이 대신할 수 없다.

- 남의 주문 조회(IDOR): 소유권은 조회 조건(`WHERE member_id = ?`)에 들어가야 한다.
- 대량 할당: 요청 전용 DTO를 따로 둔다.
- SQL 인젝션: 매개변수 쿼리(MyBatis `#{}`).
- 경쟁 조건: 조건부 UPDATE·유니크 제약. `@Idempotent`는 같은 요청의 중복만 막는다.
- 원장 대사, 망 분리·백업 같은 아키텍처.

## 주의: 끄는 스위치

랩의 `secure-kit.<부품>.enabled: false`와 `/_kit/*`(`secure-kit.lab.expose`)는 영상에서 「막기 전/후」를 보여 주려고 둔 것이다.
회사 서비스에서는 `lab.expose`를 켜지 말고, 한번 켠 보안 부품을 끄는 스위치를 설정에 남겨 두지 않는다(누군가 끄면 조용히 뚫린다).

## 확인

부품 24개의 HTTP 계약은 `packages/contract-tests/CONTRACT.md`, 시험은 `packages/contract-tests/`(Python). 예제 앱:

- `packages/examples/spring-kit-demo` — 부품 24개 전부(127.0.0.1:18181)
- `packages/examples/spring-shop` — 15-4 수업의 「내 서비스」(127.0.0.1:18183)
