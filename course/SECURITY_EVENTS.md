# SECURITY_EVENTS · 보안 이벤트 이름 목록

v0.11 · 2026-11-10(섹션 15) · 기준: `../../../랩-설계.md` 10절(형식)·11절(trace_id). **수업에서 이벤트를 하나 새로 만들면 여기에 한 줄 더한다.**

## 형식(두 스택 같음)

로거 이름 `SECURITY`, 한 줄 JSON. Spring은 `spring/mall-security/.../security/event/SecurityEventLogger.java`, Express는 `diffs/common/express-security-event.diff`의 `lib/log/securityEvent.ts`.

```json
{"event":"access.denied.not_owner","lesson":"2-2","actor":"member:102","target":"order:9001","ip":"172.27.0.1","ua":"python-httpx/0.28","reason":"본인 주문이 아니거나 없는 주문","count":1,"ts":"2026-11-07T14:10:00.123+09:00","trace_id":"…"}
```

| 키 | 내용 |
|---|---|
| `event` | 아래 표의 이름. `<영역>.<결과>.<이유>` |
| `lesson` | 그 이벤트를 넣은 수업 번호 |
| `actor` | 누가: `member:<ID>`, `admin:<계정 이름>`, `customer:<uuid>`, `anonymous` |
| `target` | 무엇을: ID만(`order:9001`, `path:/daon/customer/list`, `host:127.0.0.1`) |
| `ip` · `ua` | 요청 주소, User-Agent(120자까지) |
| `reason` | 사람이 읽는 이유(값이 아니라 설명) |
| `count` | 같은 `event`·`actor`가 최근 10분 안에 몇 번째인지(앱 인스턴스 하나 기준) |
| `ts` · `trace_id` | 시각(+09:00), 요청 추적 번호. 요청 맨 앞 필터가 정한 값(`X-Trace-Id` → `traceparent` → `X-Request-Id` → 새로 만듦, 모양이 이상하면 버림)으로 응답 헤더 `X-Trace-Id`·큐 메시지 헤더와 같다(랩-설계 11절) |

넣지 않는 것: 개인정보 원문(이름·전화·주소), 토큰·비밀번호, 요청 본문 전체, 보낸 값(예: 포인트 999999). 로그 수준: 거부 WARN, 의심 INFO.

## 이벤트 목록

| event | 수준 | 수업 | 남기는 곳(Spring) | 뜻 | 경보 후보(8장) |
|---|---|---|---|---|---|
| `access.denied.not_owner` | WARN | 2-2 | `OmsPortalOrderServiceImpl.detail()` | 남의 주문(또는 없는 주문) 조회를 거부 | 같은 actor `count` ≥ 5 / 10분(번호 훑기, P2) |
| `access.denied.not_owner` | WARN | 2-mission | `OmsPortalOrderReturnApplyServiceImpl.create()` | 남의 주문으로 반품 신청을 거부 | 위와 같음 |
| `access.denied.no_permission` | WARN | 2-3 | `DynamicAuthorizationManager` | 로그인한 관리자가 권한표에 없는 주소·역할 밖 주소를 부름 | 같은 actor `count` ≥ 3 / 10분 |
| `input.unexpected_fields` | INFO | 2-4 | `UmsMemberServiceImpl.updateInfo()` | 요청 전용 DTO에 없는 항목이 섞인 수정 요청(무시하고 처리) | 한 번이라도(정상 화면은 보내지 않는다, P3) |
| `egress.denied.internal_address` | WARN | 2-5 | `DaonImageFetcher` | 서버가 대신 부를 주소가 내부 주소 | 한 번이라도(P1) |
| `egress.denied.scheme` · `egress.denied.bad_url` · `egress.denied.no_proxy` · `egress.denied.not_image` | WARN | 2-5 | `DaonImageFetcher` | 스킴·형식·프록시 없음·이미지 아님 | `no_proxy`는 설정 사고, 나머지는 반복 시 |
| `egress.denied.not_allowed_host` | WARN | 2-5(Express) | EverShop `secureFetch.ts` | 허용 목록 밖 호스트(리다이렉트 포함) | 반복 시 |
| `request.denied.host` | WARN | 2-6 | `DaonOpsGuardFilter` | 운영 도구에 허용하지 않은 Host(DNS 리바인딩 의심) | 한 번이라도 |
| `db.error.bad_sql_grammar` | WARN | 3-2 | mall-portal `DaonSqlErrorObserver`(예외 처리기 앞에서 관찰만, 응답은 그대로) | SQL 문법 오류. 바인딩 뒤에는 「아직 조립하는 곳」 또는 「찾고 있는 사람」. SQL 문장·입력 값은 넣지 않음 | 한 번이라도 |
| `input.rejected.sort_field` | WARN | 3-3 | `DaonProductSearchServiceImpl.search()` | 허용 목록 밖 정렬 값을 400으로 거절. 받은 값은 넣지 않음. 로그인 없는 검색이라 actor는 `anonymous` | 같은 `ip` 반복(익명은 actor로 묶이지 않는다) |
| `input.sanitized.html` | INFO | 3-4 | `DaonReviewServiceImpl.create()` | 후기 본문에 태그처럼 보이는 것. reason에 태그 이름만(`허용 밖 태그 발견: img`), target `review:<ID>` | 한 번이라도 |
| `input.sanitized.html` | INFO | 3-mission | `DaonReviewServiceImpl.create()` | 후기와 함께 저장되는 닉네임의 태그(`허용 밖 태그 발견(닉네임): b`) | 한 번이라도 |
| `csp.violation` | INFO | 3-5 | mall-admin `DaonCspReportController`(관리자 화면 nginx `/api/daon/csp-report` → `POST /daon/csp-report`) | 브라우저 CSP 위반 보고. target `directive:<지시어>`(인라인 처리기는 `script-src-attr`), reason `막힌 출처: inline`. 누구나 보낼 수 있어 INFO | 같은 페이지 반복 |
| `upload.rejected.type` | WARN | 3-6 | `MinioController.upload()`(`UploadValidator`) | 내용(앞 바이트)이 PNG·JPEG·GIF·WebP가 아님(`감지=text·markup·binary`). 파일 이름은 넣지 않음 | 같은 직원 반복 |
| `upload.rejected.size` | WARN | 3-6 | 위와 같음 | 크기 상한(2MB) 초과 → 413 | 같은 직원 반복 |
| `upload.rejected.type` | WARN | 3-6(Express) | EverShop `CustomMemoryStorage.js`(diffs/s3-06) | 위와 같음 | 같은 직원 반복 |
| `auth.login.breached_password_in_use` | INFO | 4-2 | `PasswordPolicy` | 로그인한 비밀번호가 유출 목록에 있음 | 한 번이라도 |
| `auth.password.rejected_breached` | INFO | 4-2 | `PasswordPolicy` | 유출 목록에 있는 비밀번호로 가입·변경 시도를 거절 | 같은 ip 반복 |
| `auth.login.unknown_account_burst` | WARN | 4-4 | `UnknownAccountWatch` | 없는 계정으로 로그인 시도가 짧은 시간에 여러 번 | 한 번이라도 |
| `auth.reset.code_failed_burst` | WARN | 4-5 | 비밀번호 재설정 인증 번호 | 인증 번호 연속 실패 | 한 번이라도 |
| `auth.reset.host_mismatch` | WARN | 4-5 | 재설정 링크 | 링크 주소가 서버가 정한 주소와 다름 | 한 번이라도 |
| `auth.reset.token_reused` | WARN | 4-5 | 재설정 링크 | 이미 쓴 재설정 토큰 재사용 | 한 번이라도 |
| `auth.stepup.required` · `auth.stepup.failed` | INFO · WARN | 4-7 | `StepUpGuard` | 고위험 작업에 재인증 요구 · 재인증 실패 | 실패 반복 |
| `auth.mfa.push_flood` | WARN | 4-7 | 승인 요청 | 승인 요청을 짧은 시간에 연속 전송 | 한 번이라도 |
| `account.change.high_risk` | INFO | 4-7 | 계정 변경 | 비밀번호·전화번호 등 고위험 변경 | 변경 뒤 새 기기 로그인과 함께 |
| `auth.token.revoked_used` | WARN | 4-8 | `JwtAuthenticationTokenFilter` | 로그아웃·폐기한 토큰을 사용(jti 앞 8자) | 한 번이라도 |
| `auth.refresh.reuse_detected` | WARN | 4-8 | 갱신 토큰 | 이미 쓴 갱신 토큰 재사용, 같은 계열 전체 폐기 | 한 번이라도 |
| `auth.token.denied_alg` | WARN | 5-2 | `JwtSigner.verify` | ES256이 아니거나 서명·형식이 맞지 않는 토큰. 토큰 원문은 넣지 않음 | 한 번이라도 |
| `auth.token.denied_unknown_kid` | WARN | 5-2 | 위와 같음 | 공개키 목록에 없는 kid | 한 번이라도 |
| `auth.token.denied_retired_key` | INFO | 5-2 | 위와 같음 | 폐기한 키 버전의 토큰(회전 직후에는 정상 사용자도 생김) | 겹침이 끝난 뒤에도 계속되면 |
| `auth.token.denied_no_exp` | WARN | 5-2 | `JwtTokenUtil.isTokenExpired` | exp 없는 토큰 | 한 번이라도 |
| `crypto.decrypt.failed` | WARN | 5-3 | `FieldCrypto` | 칸 값을 풀지 못함(바뀐 값·틀린 키). 암호문·평문은 넣지 않음 | 한 번이라도 |
| `pii.reveal` | INFO | 5-4 | `DaonPiiRevealController` | 전화번호 원문을 사유와 함께 한 건 봄(reason 100자까지, 번호는 넣지 않음) | 관리자 한 명이 하루 상한에 가까워지면 |
| `pii.reveal.denied` | WARN | 5-4 | 위와 같음 | 사유 없이 원문 보기 시도 | 같은 관리자 반복 |
| `pii.reveal.burst` | WARN | 5-4 | 위와 같음 | 하루 원문 보기 상한(50건) 초과 | 한 번이라도 |
| `config.startup.denied_default_secret` | WARN | 6-3 | `StartupSecretCheck`(EnvironmentPostProcessor) | 알려진 기본값 시크릿으로 기동 시도, 시작 중단. target은 설정 키 이름(값은 넣지 않음). 앱이 바로 멈추므로 마지막 줄 | 한 번이라도(배포 사고 신호) |
| `secret.lease.renew_failed` | WARN | 6-3 | `LeaseKeeper` | 동적 DB 자격증명 갱신 실패(임대가 끝나기 전 앱이 스스로 내려감). target `lease:database/creds/<앱>` | 한 번이라도(OpenBao 접근 문제·운영 사고 신호) |
| `recon.endpoint_probe` | INFO | 6-5 | `EndpointProbeFilter` | 로그인 없이 관리·문서 경로(`/actuator`·`/druid`·`/swagger-ui`·`/v3/api-docs` 등)를 요청. target `path:<경로>`. 경로는 이미 닫혀 있다. 한 번은 흔한 봇이라 INFO | 같은 IP가 10분에 서로 다른 관리 경로 N개 이상(8장에서 WARN 경보) |
| `cors.denied_origin` | INFO | 6-6 | `OriginWatchFilter` | 허용 목록 밖 출처의 교차 출처 요청. target `origin:<값 120자까지>`(공격자가 넣은 문자열일 수 있어 화면에는 textContent로만). 브라우저 확장·잘못된 설정에서도 생겨 INFO | 같은 출처가 반복되면 8장에서 확인 |
| `csrf.denied_origin` | WARN | 6-6(Express) | EverShop `originCheck`(diffs/s6-06) | 상태를 바꾸는 요청(로그인·업로드)의 Origin이 허용 목록 밖. 정상 화면에서는 생기지 않는다 | 한 번이라도 |
| `cache.integrity.failed` | WARN | 6-4 | `UmsAdminCacheServiceImpl`(`CacheHmac`) | Redis 권한 캐시의 서명 불일치·없음 → 값을 버리고 DB에서 다시 읽음. target `cache:resourceList:<관리자 번호>`. 누군가 저장소에 썼거나 전환 직후 옛 값(서명 없음) | 한 번이라도 경보 후보(전환 때는 캐시를 비운다) |
| `secret.canary_used` | WARN | 6-2 | `JwtAuthenticationTokenFilter`(`CanaryTokens`) | 저장소·문서에 일부러 남긴 미끼 토큰이 요청에 쓰임(401). target `canary:<이름>`. 진짜 토큰이 아니라 사람이 알아보는 표시 문자열 | 한 번이라도(저장소나 문서가 밖으로 샜다는 신호) |
| `supply.kev_match` | WARN · INFO | 7-2 | `make kev`(도구) | SBOM의 부품이 KEV 목록(악용 중 취약점)과 일치. 결정이 없거나 예외 기한이 지났으면 WARN, 결정이 있으면 INFO. target은 패키지 주소(`pkg:maven/<그룹>/<이름>@<버전>`). 도구 이벤트라 `reports/security-events.jsonl`에 남는다 | 결정 없음·기한 지남 WARN은 한 번이라도 |
| `supply.upstream_patch.overdue` | INFO · WARN | 7-3 | `make upstream-status`(도구) | 원본 보안 패치를 반영하지 않은 채 48시간(INFO)·72시간(WARN)이 지남. actor `tool:upstream-status`, target `upstream:mall@<커밋 앞 12자>` | WARN 한 번이라도 |
| `ci.denied.unpinned_step` | WARN | 7-5 | `tools/ci/check_pins.py`(CI 첫 작업·`make ci-lint`) | 외부 단계(`uses:`)가 40자 커밋 해시가 아닌 태그·브랜치로 적힘. actor `git:lab`, target `workflow:build.yml#<줄>` | 같은 actor 3회 / 1일 |
| `ci.step.changed` | WARN | 7-5 | 위와 같음 | 워크플로의 해시가 `ci/pins.lock`과 다름(락 파일 없이 해시만 바꿈) | 한 번이라도 |
| `deploy.denied.unsigned_image` | WARN | 7-6 | `tools/deploy/deploy.sh`(`make deploy`) | 서명이 없거나·출처 증명의 커밋이 main에 없거나·확인 자체를 못 해서 배포 거부. actor `deploy:lab`, reason은 「서명 없음」·「출처 증명의 커밋이 main에 없음」·「확인 불가」, target `image:daon/<서비스>@sha256:<앞 12자>` | 한 번이라도(P1) |
| `deserialize.denied.type` | WARN | 7-7(7-미션) | `CacheTypeGuard`(+ `BaseRedisConfig` 허용 목록) | Redis 캐시 값의 타입이 허용 목록 밖이거나 글자가 아님(예전 형식·남이 쓴 값) → 키를 지우고 DB에서 다시 읽음. actor `system:redis-cache`, target `cache:<키 앞부분>:* class:<이름>`(이름은 영문·숫자·`.$_` 120자까지). 전환 직후 예전 형식 값은 한 번 남는다 | 정상 운영에서는 나올 일이 없다: 한 번이라도 |
| `config.signature.invalid` | WARN | 7-미션 | `SecureKitSwitch`(`SwitchSignature`) | 방어 스위치 파일의 서명이 맞지 않아 꺼짐 값을 믿지 않고 기본값(켜짐)으로 시작. actor `system:startup`, target `config:switches.env` | 한 번이라도(설정 파일 변조 신호) |
| `data.read.bulk` | INFO | 8-3 | `AuditAspect`(mall-security, 관리자 조회의 `@Audited`) | 한 요청에서 기준(랩 30건, `DAON_AUDIT_BULK_THRESHOLD`) 이상 개인정보 조회. actor `admin:<계정>`, target `action:<감사 행동>`(예: `action:customer.list`), count는 같은 actor의 10분 안 몇 번째인지(건수는 AUDIT 줄에) | 같은 actor AUDIT `customer.*` `count` 합계 > 30 / 10분(P2, rules/bulk.yaml에 손으로 씀) |
| `auth.denied.token` | WARN | 8-4(Express 8-3) | `RestAuthenticationEntryPoint`(HTTP 401, F32), EverShop `auth.ts`(diffs/s8-04) | 토큰 없음·만료·서명 불일치로 로그인이 필요한 주소를 거부. actor `anonymous`, target `path:<경로>`(영문·숫자·`/._-`만 120자), reason 「토큰 없음」·「토큰 만료·서명 불일치」. 토큰 값은 넣지 않음 | 같은 ip 20회 / 10분(P2) |
| `log.leak.masked_at_collector` | WARN | 8-5 | 수집기(Alloy) 가리기 단계 → 줄에 `masked_at_collector="true"` 표시 → Grafana 규칙 | 앱이 놓친 민감 값(JWT 모양·휴대전화 모양·password 값)을 수집기가 가림. 값은 남기지 않고 무엇(leak=jwt·phone·password)과 어느 앱(app)만 | 한 번이라도(P2, rules/collector.yaml에 손으로 씀) |
| `audit.chain.broken` | WARN | 8-6 | `make audit-verify`(도구) | 감사 로그 해시 체인이 끊김(중간 줄 지움·바뀜) 또는 머리 해시 불일치(끝 줄 지움: Loki 사본의 마지막 seq와 다름). actor `tool:audit-verify`, target `audit:mall-admin#seq=<번호>`, reason 「앞 줄 해시 불일치」·「머리 해시 불일치」 | 한 번이라도, 즉시(P1) |
| `alert.escalated.unacknowledged` | WARN | 8-7 | `make drill-report`(도구) | P1 경보가 기준 시간(랩 15분, `DAON_ACK_LIMIT_MIN`) 안에 확인(`make ack`)되지 않음 → 한 단계 위 담당에게 다시 보냄. target `alert:<경보 이름>` | 한 번이라도(P1, 다른 담당 lead@) |
| `auth.fail_closed.dependency_error` | WARN | 9-2 | `DaonSmsVerifyController`(`DaonStepUpClient`가 「확인 불가」를 던짐) | 본인 확인 서버(문자 사업자) 오류·시간 초과로 고위험 작업의 본인 확인을 거부(HTTP 503). actor `member:<ID>`, target `dependency:sms-stub`, reason 「본인 확인 서버 오류로 거부(응답 500)」·「…(시간 초과)」. 공격보다 장애일 때가 많다 | 같은 actor 10회 / 10분(P2) |
| `input.denied.too_large` | WARN | 9-3 | `RequestSizeLimitFilter`(mall-security, 두 앱) | 요청 본문이 상한(1MB) 초과 → 413. Content-Length와 청크 전송(읽은 바이트) 둘 다. 바이트 수 대신 구간(「1~10MB」)만. 보안 필터 앞이라 actor는 `anonymous` | 같은 ip 5회 / 10분 |
| `input.denied.page_size` | WARN | 9-3 | `PageSizeLimitFilter`(mall-security, 두 앱) | 목록 API의 pageSize가 1~100 밖 → 400(F41). target `path:<경로>`. 클라이언트 버그로도 생긴다 | 같은 ip 10회 / 10분 |
| `input.denied.too_long` | WARN | 9-3 | `DaonCouponController` | 쿠폰 코드가 길이 상한(20자) 초과 → 정규식 검사 전에 400(T23). target `field:couponCode`, 받은 값은 넣지 않음 | 같은 ip 5회 / 10분 |
| `input.denied.quantity` | INFO | 9-3 | `OmsCartItemController`(담기·수량 수정) | 수량이 1~99 밖 → 400(F42). 정상 화면은 보내지 않는 값 | 같은 actor 반복 시(P3) |
| `input.denied.graphql_depth` | WARN | 9-3(Express) | EverShop `graphqlMiddleware.js`(diffs/s9-03) | GraphQL 질의 깊이 상한 초과 → 400(E10) | 같은 ip 5회 / 10분 |
| `resilience.circuit.opened` | WARN | 9-5 | `DaonPaymentClient`(Resilience4j 상태 바뀜) | 결제사 실패 비율이 기준(최근 10번 중 50%) 이상이라 20초 동안 부르지 않음. actor `system:pay-worker`, target `dependency:pay-stub`. 장애 신호 | 한 번이라도(P2) |
| `resilience.circuit.closed` | INFO | 9-5 | 위와 같음 | 시험 호출이 성공해 다시 부름 | 경보 없음(열림 경보가 풀렸다는 표시) |
| `resilience.retry_budget.exhausted` | WARN | 9-5 | `DaonPayRequestReceiver`(`RetryBudget`) | 최근 1분 재시도가 예산(새 요청의 10%, 최소 3번)을 다 써서 다시 보내지 않고 DLQ로 | 한 번이라도(P2) |
| `queue.dead_lettered` | WARN | 9-5 | `DaonPayRequestReceiver` | 결제 요청이 끝내 실패해 DLQ(`daon.pay.dlq`)로 감. target `queue:daon.pay.request`, reason에 이유와 주문 번호, trace_id는 메시지 헤더 값 | 같은 actor 10회 / 10분(P2) |
| `coupon.denied.limit_reached` | WARN | 10-2 | `UmsMemberCouponServiceImpl.add()` | 쿠폰 받기 거절: 1인 한도(이미 받음·유니크 제약 중복 키) 또는 수량 소진(조건부 갱신 0행). actor `member:<ID>`, target `coupon:<ID>`. 정상 사용자는 「이미 받았습니다」를 한두 번 보는 데서 끝난다 | 같은 actor 3회 / 1분(동시 요청 시도) |
| `points.denied.insufficient` | WARN | 10-2 | `OmsPortalOrderServiceImpl`(주문 포인트 차감) | 포인트 조건부 차감 0행(잔액 부족 또는 동시 사용) → 주문 전체 되돌림. actor·target `member:<ID>`(주문은 아직 없음). 쓴 포인트 값은 넣지 않음 | 같은 actor 3회 / 1분 |
| `payment.denied.client_call` | WARN | 10-4 | `OmsPortalOrderController.paySuccess()`(지운 자리의 얇은 거절 처리기, HTTP 410) | 회원이 없앤 「결제 성공」 API를 부름(F02). 정상 고객 웹은 더 이상 부르지 않는다. actor `member:<ID>`, target `order:<ID>` | 한 번이라도(P2) |
| `payment.denied.bad_signature` | WARN | 10-4 | `DaonPaymentWebhookController`(`DaonWebhookVerifier`) | 결제 웹훅 서명(X-Daon-Signature, HMAC-SHA256) 불일치 → 401. actor `anonymous`, target `path:/pay/webhook` | 한 번이라도(P1) |
| `payment.duplicate.event` | INFO | 10-4 | `DaonPaymentService.apply()`(`oms_payment_event` 중복 키) | 같은 (결제 번호, 이벤트) 웹훅이 다시 옴 → 처리 없이 200. 정상 재전송일 수 있어 의심 수준. actor `system:pay-webhook`, target `payment:<결제 번호>` | 같은 actor count ≥ 5 / 10분(결제사 장애·재처리 오류) |
| `payment.denied.amount_mismatch` | WARN | 10-4 | `DaonPaymentService.apply()` | 웹훅 승인 금액 ≠ 주문 결제 금액 → 상태 그대로, 422. target `order:<ID>`. 금액 값은 넣지 않음 | 한 번이라도(P1) |
| `payment.denied.bad_transition` | WARN | 10-4 | `DaonPaymentService.apply()`(조건부 UPDATE `status = 0` 0행) | 결제 완료로 갈 수 없는 상태(이미 결제 완료·취소)에서 결제 알림 → 409. target `order:<ID>`, reason 「status=0 조건 0행(지금 상태 N…)」 | 반복 시 |
| `ledger.mismatch` | WARN | 10-5 | mall-admin `DaonReconcileJob`(매일 04:00 + `POST /daon/reconcile/run`) | 대사 불일치: 회원 잔액 ≠ 포인트 원장 합계(target `member:<ID>`), 또는 결제 완료 주문에 결제사 승인 기록이 없거나 금액이 다름(target `order:<ID>`). actor `system:reconcile`. 불일치 하나에 한 줄, 50줄 뒤에는 요약 한 줄(target `reconcile:summary`, count에 전체 수). 이름은 `<영역>.<결과>` 두 마디(계획 이름 그대로, 작성자 결정 대기) | 한 번이라도(P2) |
| `checkout.denied.rate_limit` | WARN | 10-6 | `DaonCheckoutLimiter`(`/pay/start` 앞) | 결제 시도 한도 초과 → 429. 축은 계정 5회/10분·IP 20회/10분·카드 지문 3계정/1일. reason에 축 이름만(값 없음), actor `member:<ID>`, target `path:/pay/start` | 같은 actor 3회 / 10분 |
| `purchase.denied.per_member_limit` | WARN | 10-6 | `OmsPortalOrderServiceImpl.generateOrder()`(`daon_purchase_counter` 조건부 갱신 0행) | 한정 상품 1인 구매 한도 초과(랩 값 상품 230 1인 2개). target `product:<ID>` | 반복 시 |
| `payment.suspected.card_testing` | WARN | 10-6 | `DaonCheckoutLimiter.record()` | 10분 창에서 결제 시도 10번 이상, 거절이 절반 넘음(창마다 한 번). actor `system:checkout`, count에 거절 수. 기준선은 랩 고정값(운영은 시간대별) | 한 번이라도(P1) |
| `budget.denied.sms` | WARN | 10-7 | `DaonSmsBudget`(`/sso/getAuthCode` 앞) | 인증 문자 발송 거절: 번호당(60초 1번·하루 5번)·IP당(10분 30번) 한도, 국내 휴대전화 형식 아님, 전체 상한으로 멈춘 시간. target `phone:<SHA-256 앞 8자>`(번호 원문 없음), 로그인 전이라 actor `anonymous` | 같은 ip 10회 / 10분 |
| `budget.tripped.global` | WARN | 10-7 | `DaonSmsBudget` | 전체 시간당 발송 상한(랩 20건)에 닿아 그 시간 끝까지 발송 중지(정상 사용자도 못 받음 → 사람이 본다). actor `system:sms`, target `budget:sms` | 한 번이라도(P1) |
| `price.tampered` | WARN | 10-8 | `OmsCartItemController`(담기·규격 바꾸기, 서버 가격으로 처리) · `OmsPortalOrderServiceImpl.generateOrder()`(주문 직전 대조, 주문 거절) | 요청이 보낸 가격 ≠ SKU 가격(F45). actor `member:<ID>`, target `sku:<ID>`. 보낸 가격 값은 넣지 않음. 고객 웹은 가격을 보내지 않으므로 정상 사용자에게서 나올 수 없다. 이름은 두 마디(계획 이름 그대로, 작성자 결정 대기) | 한 번이라도(P2) |
| `redirect.denied.not_allowed` | WARN | 11-2 | `DaonRedirectController.go()`(`SafeRedirect.resolve`, mall-portal) | 로그인 뒤 이동 주소(`/daon/go?next=`)가 규칙 밖 → 이동하지 않고 `/`로. target `path:/daon/go`, reason 「허용 목록 밖 이동 주소(규칙 0·1·2)」(0 파싱 실패, 1 상대 경로 규칙, 2 외부 주소 규칙). **받은 주소 원문은 넣지 않는다**(공격자가 넣은 값, 로그 줄 위조 통로). 로그인 전이면 actor `anonymous` | 같은 ip count ≥ 5 / 10분(피싱 링크를 만들어 보는 시도) |
| `request.denied.malformed` | WARN | 11-3 | 게이트웨이 nginx(`log_format daon_sec_malformed`, 고객 API 입구 8086) | 요청 경계가 모호한 요청(길이 표시 둘, Content-Length 두 줄, 접힌 헤더 줄, chunked가 아닌 Transfer-Encoding)을 게이트웨이가 400·501로 끊음(뒤 서버에 가지 않음). 앱이 아니라 게이트웨이 접근 로그에 같은 키의 「WARN SECURITY {…}」 한 줄. actor `anonymous`, target `path:<경로>`(영문·숫자·`/._-` 120자), count는 늘 1(nginx는 세지 못한다. 경보 규칙이 ip로 묶는다) | 같은 ip 3회 / 10분 |
| `cache.hit.authenticated` | WARN | 11-4 | 게이트웨이 nginx(`log_format daon_sec_cache_hit`) | 로그인한 요청(Authorization 있음)인데 Varnish가 캐시에서 답함(`X-Cache: HIT`) = 남의 응답이 나갔을 수 있다. 캐시 단계에서는 회원을 모르므로 actor `anonymous`, target `path:/api/sso/info`. 11-4 뒤 정상 운영에서는 0(배포 직후 캐시 설정이 바뀌는 사고 신호) | 한 번이라도(P1) |
| `config.audit.failed` | WARN | 11-5 | `tools/ci/audit_dns.py`(`make audit-dns`·CI 검사 작업) · `make audit-mail`(도구) | 설정 점검 실패 한 건에 한 줄. DNS: 주인 없는 레코드(target `dns:<이름>`), 목록 밖 SPF include(`spf:<include>`), 와일드카드. 메일: 패치 전 버전·bare LF 허용·DMARC p=none(target `mail:<설정 이름>`·`dns:_dmarc.daon.example`). actor `ci:audit-dns`(CI 안)·`tool:audit-dns`·`tool:audit-mail`. 도구 이벤트라 `reports/security-events.jsonl` | 한 번이라도(P2) |
| `partner.denied.scope` | WARN | 12-2 | `PartnerScopeInterceptor`(secure-kit, `@PartnerScope`) | 파트너 토큰에 없는 범위의 기능을 부름 → 403(예: 배송 파트너가 `/partner/orders/export`). actor `partner:<앱>`, target `path:<경로>`, reason에 필요한 범위 이름. 정상 파트너 앱은 범위 밖을 부르지 않는다 | 한 번이라도(P2) |
| `partner.denied.token_expired` | INFO | 12-2 | `PartnerScopeInterceptor` | 만료된 파트너 토큰(15분) → 401. 재발급 직전 한두 번은 정상 | 같은 actor count ≥ 20 / 10분(파트너 앱 오류 또는 토큰 재사용 의심) |
| `partner.denied.bad_credential` | WARN | 12-2 | `DaonPartnerTokenController`(`POST /partner/token`·`/partner/credentials/rotate`) | 파트너 비밀값이 틀림(겹침 기간이 지난 옛 비밀값 포함) 또는 없는 파트너 → 401. 없는 client_id는 받은 값을 넣지 않고 actor·target `partner:unknown`. 계획에 없던 이벤트(12-2에서 더함) | 같은 actor count ≥ 5 / 10분 |
| `access.denied.not_owner` | WARN | 12-2 | `DaonPartnerController.order()` | 파트너에게 배정되지 않은(또는 없는) 주문 상세 → 404. 2-2와 같은 이벤트, actor만 `partner:<앱>` | 위와 같음(2-2 줄) |
| `token.denied.binding_mismatch` | WARN | 12-3 | `PartnerScopeInterceptor` · `DaonPartnerTokenController`(발급) | 토큰의 `cnf`(x5t#S256)와 파트너 입구가 확인한 클라이언트 인증서 지문이 다름·인증서 없음·묶이지 않은 토큰 → 401. 발급 때는 인증서 없음·CN과 client_id 다름. actor `partner:<토큰 주인>`. 인증서 원문·지문 값은 넣지 않는다. 정상 상황에서는 나오지 않는다(같은 파트너 토큰 즉시 폐기 후보, 12-4) | 한 번이라도(P1) |
| `partner.export` | INFO | 12-4 | `DaonPartnerController.export()` | 파트너 내보내기 한 번에 한 줄. **count에 내보낸 행 수**(이 이벤트만 「최근 10분 횟수」가 아니다). 응답 본문(주문·받는 사람)은 넣지 않는다. actor `partner:<앱>`, target `path:/partner/orders/export` | `rules/partner.yaml`(파트너별 10분 행 수 합계 > 기준선 100의 3배, partner.export.bulk) |
| `partner.app.changed` | WARN | 12-4 | mall-admin `DaonPartnerAdminController`(`/daon/partner/apps/{id}/scopes`·`/revoke`) | 연동 앱 범위 확대(reason `+orders:export`처럼 더한 범위 이름) 또는 앱 단위 일괄 폐기(reason에 끊은 토큰·비밀값 수). 줄이는 변경은 남기지 않는다. actor `admin:<계정>`, target `partner:<앱>` | 한 번이라도(P2) |
| `partner.denied.revoked` | WARN | 12-4 | `PartnerScopeInterceptor`(폐기 목록 `deny:<jti>`) · `DaonPartnerTokenController`(상태 revoked) | 폐기한 앱의 토큰이 다시 쓰이거나 폐기한 앱이 토큰 발급을 청함 → 401. 폐기 뒤에 나오면 도난 토큰 사용 신호 | 한 번이라도(P1) |
| `approval.denied.over_remaining` | WARN | 13-3 | 승인 서비스 `ApprovalController.refund()`(`ApprovalPolicy.evaluate()`) | 환불 요청 금액이 남은 결제 금액(주문 결제 금액 − 승인 서비스가 이미 잡아 둔 환불 합계, 승인 서비스가 DB에서 직접 계산)을 넘음 → 거부. actor `admin:<요청자>`(관리자 토큰을 승인 서비스가 직접 확인), target `return:<ID>`. 금액은 넣지 않는다. trace_id는 관리자 앱이 받은 값 그대로 | 한 번이라도(P1) |
| `approval.denied.policy_limit` | WARN | 13-3 | 승인 서비스 `ApprovalPolicy.evaluate()` · `ApprovalController.approve()`(13-4)·`ElevationController.approve()`(13-6) | 정책 파일 위반: 1회 한도·같은 주문 하루 횟수·결제가 끝나지 않은 주문·원 단위가 아닌 금액, 또는 두 번째 승인·권한 상승 승인 역할이 아님. reason에 규칙 이름만 | 같은 actor count ≥ 2 / 10분 |
| `refund.denied.no_approval` | WARN | 13-3 | mall-admin `DaonRefundExecutor.execute()`(`POST /daon/refund/execute`·13-4 지연 실행 실행기) | 승인 토큰이 없거나, 서명이 맞지 않거나(다른 키·내용을 바꾼 토큰), 내용(반품 ID·주문·상태)이 DB와 다르거나, 만료됐거나, 1회용 번호를 다시 씀 → 결제사를 부르지 않음(403). target `return:<ID>`, 토큰 원문은 넣지 않는다. 정상 흐름에서는 나오지 않는다 | 한 번이라도(P1) |
| `approval.denied.self_approval` | WARN | 13-4 | 승인 서비스 `ApprovalController.approve()` · `ElevationController.approve()`(13-6, lesson 13-6) | 요청한 사람이 스스로 두 번째 승인(고액 환불)·권한 상승 승인을 시도 → 403. target `approval:<ID>` | 한 번이라도(P2) |
| `approval.denied.bad_confirm` | WARN | 13-4 | 승인 서비스 `ApprovalController.approve()`(`OutOfBandConfirm.check()`) | 메일로 보낸 확인 번호가 없거나 틀림·만료·5번 넘게 시도 → 403. 번호 값은 넣지 않는다. 계획에 없던 이벤트(13-4에서 더함) | 같은 actor count ≥ 3 / 10분 |
| `approval.pending.second_approver` | INFO | 13-4 | 승인 서비스 `ApprovalController.refund()` | 고액(가짜 기준 300,000원 이상)이라 두 번째 승인자를 기다림. 확인 번호는 메일(Mailpit)로만 | — (감사용) |
| `approval.cancelled.in_window` | INFO | 13-4 | 승인 서비스 `ApprovalController.cancel()` | 지연 실행 창(랩 10초·회사 30분) 안에서 요청자·승인자·취소 역할이 취소 | 한 번이라도(누군가 이상을 알아챘다는 뜻, P2) |
| `payout.breaker.open` | WARN | 13-4 | 승인 서비스 `PayoutCircuitBreaker.allow()` | 창(1시간) 안 환불 승인 건수·합계가 기준(가짜 6건·1,000,000원)을 넘어 자동 실행을 멈춤(이후 HELD). 닫기는 서로 다른 두 사람(`POST /breaker/close`) | 한 번이라도(즉시, P1) |
| `auth.denied.disabled_account` | WARN | 13-6 | mall-admin `UmsAdminServiceImpl.login()` | 닫은 공용 계정(daon_ops)·원본 데모 관리자로 로그인 시도. 비밀번호가 맞든 틀리든 같은 문구로 거부. actor·target `admin:<계정>` | 한 번이라도(P1) |
| `ops.denied.no_elevation` | WARN | 13-6 | mall-security `DynamicAuthorizationManager`(고위험 목록 `OpsElevationGate`) · mall-admin `DaonOpsElevationService.activate()` | 역할은 있지만 권한 상승 없이 고위험 기능(관리자 계정·역할·권한표 바꾸기)을 부름 → 403, 또는 서명·주인이 맞지 않는 권한 상승 토큰을 켜려 함. target `path:<경로>` | 같은 actor count ≥ 3 / 10분 |
| `ops.elevation.granted` | INFO | 13-6 | mall-admin `DaonOpsElevationService.activate()` | 다른 사람이 승인한 시간 제한 권한 상승을 켬(묶음·승인자). 사유는 승인 서비스 표(감사용)에만 | — (감사용, 14장 타임라인 재료) |
| `backup.denied.delete` | WARN | 14-2 | `backup-audit`(백업 저장소 backup-vault의 감사 로그 webhook → SECURITY 줄, `infra/backup/audit.py`) | 잠금 버킷 `daon-backup`에 지우기(DeleteObject·DeleteObjects 등)를 시도했는데 거부됨(권한 없음·보존 기간 안 잠금). actor `minio:<접근 키>`, target `object:daon-backup/<키>`, trace_id `minio-<요청 번호>`. 정상 운영에서 백업을 지우는 사람은 없다 | 한 번이라도(P1) |
| `backup.missed` | WARN | 14-2 | `backup-audit`(30초마다 확인) | backup-writer가 `mysql/`에 올린 마지막 백업이 기준(랩 15분, `DAON_BACKUP_MAX_AGE_SECONDS`)보다 오래됨. 백업이 조용히 멈춘 것도 사고다(지우지 못하는 공격자는 새 백업을 막는다). actor `system:backup-audit`, target `bucket:daon-backup` | 한 번이라도(P2) |
| `backup.restore.drill` | INFO | 14-2 | 도구 `make restore-drill`(`daon/backup.py`, 도구 이벤트 reports/security-events.jsonl) | 복구 훈련 결과: 통과·실패, RTO·RPO(초). target `backup:daon-backup/mysql/<키>` | — (훈련 기록. 숫자가 나빠지면 사람이 본다) |
| `ops.mode.degraded` | WARN | 14-3 | mall-admin `DaonModeController`(`POST /daon/mode/payment`) | 사람이 결제 축소 운영을 켬(권한 상승 뒤). 주문은 대기 큐, 환불·정산 503, 출금 서킷 브레이커 OPEN. actor `admin:<계정>`, target `feature:payment`. 사유는 감사 로그 `ops.mode.set`에만. 사고 선언 후보 | 한 번이라도(P1) |
| `resilience.bulkhead.rejected` | INFO | 14-3 | mall-portal `DaonPaymentGateway.status()` | 결제 확인 동시 자리(6) 초과 → 기다리지 않고 「확인 중」. 10초에 한 줄로 묶고 `count` = 그동안 넘친 횟수. 사람이 축소 운영을 켜기 전에 먼저 오른다. actor `system:mall-portal`, target `feature:payment` | 같은 actor 3회 / 10분(P2) |
| `incident.actor.locked` | INFO | 14-4 | mall-admin `DaonIncidentController.lockActor()`(`POST /daon/incident/lock-actor`) | 사고 대응 격리: 회원 잠금 + 그 회원의 토큰 일괄 폐기(계정 단위 `deny:sub:`). actor `admin:<계정>`, target `member:<ID>`, reason에 사고 번호. 대응 행동도 타임라인의 한 줄 | — (감사용, 대응 기록) |
| `auth.denied.disabled_account` | WARN | 14-4 | mall-portal `UmsMemberServiceImpl.login()` | 잠긴 회원(사고 대응 격리)의 로그인 시도. 비밀번호가 맞아도 같은 문구로 거부. actor·target `member:<ID>`. 13-6과 같은 이벤트(관리자 앱 쪽) | 위와 같음(13-6 줄) |
| `auth.token.revoked_used` | WARN | 14-4 | mall-security `JwtAuthenticationTokenFilter`(`TokenService.cutOff()`) | 격리로 끊은 계정의, 끊기 전에 로그인해 받은 토큰이 다시 쓰임(잠금을 푼 뒤에도). 4-8과 같은 이벤트 | 위와 같음(4-8 줄) |
| `secret.found.repository` | WARN | 15-1 | 도구 `daon/capstone.py`(CI 시크릿 스캔 흉내: 6-2 규칙 `.gitleaks.toml`로 로컬 Gitea 저장소 이력을 훑음, `python -m daon gitea scan`과 같은 규칙) | 저장소 커밋에 평문 자격증명. actor `git:<커밋 작성자>`, target `repo:<저장소>@<커밋 앞 12자>#<파일>`, reason에 규칙 이름만(값 없음). 도구 이벤트(reports/security-events.jsonl) | — (도구 기록. 경보 규칙은 작성자 결정 대기) |
| `incident.actor.locked` | INFO | 15-2 | mall-admin `DaonIncidentController.lockActor()`(`{"admin": …}`) | 직원 계정 격리: 상태 0 + 관리자 토큰(aud daon-admin) 계정 단위 폐기. target `admin:<계정>`. 14-4와 같은 이벤트(회원은 `member:<ID>`) | — (감사용, 15장 타임라인 재료) |
| `incident.data.restored` | INFO | 15-2 | mall-admin `DaonIncidentController.restoreColumns()` | 골라 복구: 사고 직전 백업에서 확인한 주문의 받는 사람 칸만 되돌림. target `incident:<사고 번호>`, reason에 건수만. 주문별 기록은 감사 로그 `incident.restore.columns` | — (감사용) |

> 6-4에서 새로 생긴 SECURITY 이벤트는 없다(`cache.integrity.failed`가 6-4 몫). 저장소 쪽 신호(MongoDB·RabbitMQ 인증 실패 로그, Redis `ACL LOG`)와 게이트웨이 접근 로그(JSON, `trace_id`=`$request_id`)는 앱 SECURITY 줄이 아니라 각 컨테이너의 로그다. `make logs S=gateway`로 본다. 모으는 일은 8-4(A5)에서 한다: 수집기가 모든 다온 컨테이너 로그를 Loki에 모은다(게이트웨이·이그레스 접근 로그는 stream=app).

> 7장의 `supply.*`·`ci.*`·`deploy.denied.*`는 앱 SECURITY 로그가 아니라 도구가 남기는 한 줄 JSON입니다(같은 키). 화면에 찍고 `reports/security-events.jsonl`에도 덧붙입니다(git에 넣지 않음). 8-4에서 수집기가 이 파일을 읽습니다. CI 안에서는 작업 로그가 기록이고, `make ci-run`이 PC의 파일에도 모읍니다.

## 확인

수업마다 `make check`에 「재연 뒤 앱 로그에 그 event가 남았는지」 보는 시험이 하나 있다(`tests/test_s2_0N_*.py`·`test_s3_0N_*.py`의 `*_logged`, `replay/_logs.py`). 요청 하나의 `X-Trace-Id`가 그 줄의 `trace_id`와 같은지는 `tests/test_s2_02_trace_id.py`가 본다. 화면으로는 `make lab`(랩 상황판, 최근 50건). 이그레스 프록시는 따로 `make logs S=egress | grep TCP_DENIED`.

방어 스위치(`make switch`, 랩-설계 12절)를 끄면 그 보호가 막지 않으므로 위 이벤트도 남지 않는다. 스위치 자체는 SECURITY 이벤트가 아니고, 앱이 뜰 때 `DAON-LAB` 로거가 꺼진 스위치를 한 줄로 알린다.

## 8장: 모으기와 경보(A5)

- 8-4부터 수집기(Alloy)가 앱의 SECURITY 줄(stream=security), 감사 로그 파일(stream=audit, 8-3), 도구 이벤트 파일(stream=tool)을 Loki 한 곳에 모은다. 레이블은 `app`·`stream`·`event`·`level`·`lesson`뿐이다.
- **경보 규칙은 이 표에서 만든다.** `make alert-rules`가 「경보 후보(8장)」 칸을 읽어 `infra/observability/rules/security.yaml`을 만든다(`tools/alert_rules.py`의 읽는 법). 칸에 `(P1)`·`(P2)`·`(P3)`를 적으면 등급(severity)이 된다(없으면 P2). 새 이벤트를 만들면 이 표에 한 줄 더하고 `make alert-rules`를 다시 돌린다.
- 칸 글자가 규칙으로 바뀌지 않는 줄(「N개 이상」, 「가까워지면」 등)은 `make alert-rules`가 목록으로 알려 준다. 사람이 판단하거나 손으로 쓴 규칙 파일(`rules/bulk.yaml`·`rules/collector.yaml`)에 둔다.
- AUDIT 줄은 SECURITY 이벤트가 아니다. 별도 로거(`AUDIT`)와 별도 파일·스트림이고, 형식은 `docs/audit-schema.yaml`(8-2)이 정한다.

## 9장: 예외와 장애

- 9장의 `resilience.*`·`queue.dead_lettered`·`auth.fail_closed.*`는 공격보다 **장애 신호**일 때가 많다. 그래도 SECURITY 줄로 같은 형식에 남겨, 8-4 대시보드에서 「보안 이벤트와 장애 이벤트가 같은 시각에 몰리는지」를 본다(장애를 틈탄 공격, 공격이 만든 장애를 가르기).
- `error.unhandled`(ERROR, 9-2)는 표에 넣지 않는 **운영 이벤트**다. `GlobalExceptionHandler`(mall-common)의 마지막 처리기가 로거 `OPS`로 같은 키(`event`·`lesson`·`target`=`handler:<컨트롤러.메서드>`·`reason`=예외 클래스 이름·`ts`·`trace_id`)의 한 줄을 남긴다. mall-common은 mall-security(SecurityEventLogger)를 쓰지 못한다. 수집기에서는 stream=app으로 들어간다. 응답에는 예외 이름·문구를 싣지 않고 500 + `trace_id`만 준다.

## 10장: 돈이 오가는 기능

- 10장 이벤트의 target에는 ID만 넣는다(`coupon:101`·`order:9301`·`payment:PAY000001`·`sku:2002`·`product:230`·`phone:<해시 8자>`). 금액·포인트·보낸 가격·전화번호·카드 토큰은 넣지 않는다(카드는 지문도 넣지 않는다).
- 거절 이벤트가 한 번에 많이 쌓이는 수업이 있다(10-2의 99줄, 10-7의 30줄). 로그를 줄이려고 이벤트를 빼지 않는다. `count`와 경보 규칙이 묶어 준다.
- `ledger.mismatch`는 mall-admin이 남긴다(대사 작업). 나머지는 mall-portal.
- `ledger.mismatch`·`price.tampered`는 수업 계획이 정한 이름이라 두 마디다. `price.denied.tampered`(거절)·`price.suspected.tampered`(서버 가격으로 처리)로 나눌지는 작성자 결정이 남아 있다. 이름을 바꾸면 이 표·코드·시험·경보 규칙(`make alert-rules`)을 함께 바꾼다.
- Express diff(`diffs/s10-*`)도 같은 이름을 쓴다. 10-7 Express는 문자 대신 재설정 메일 예산이라 reason에 「Express: reset-mail」을 적는다.

## 11장: 두 번 해석되는 요청

- `request.denied.malformed`·`cache.hit.authenticated`는 **앱이 아니라 게이트웨이(nginx)**가 남긴다. nginx `log_format`(escape=json)으로 앱 SECURITY 줄과 같은 키를 맞추고, 줄 앞에 `WARN SECURITY `를 붙여 수집기(A5)가 Express 줄과 같은 방법으로 stream=security·event 레이블을 붙인다. `make lab`은 A7이 켜져 있으면 게이트웨이 줄도 함께 읽는다.
- nginx는 횟수를 세지 못해 `count`가 늘 1이다. 경보 규칙(`make alert-rules`)은 같은 ip의 10분 합계로 묶는다. trace_id는 게이트웨이의 `$request_id`다(뒤 서버에 X-Request-Id로 넘긴다).
- 11-3 랩은 모호한 요청을 일부러 만들어 보내지 않는다(재연은 설정 점검). `make check L=11-3`의 「방어 확인」 세 시험만 거절과 이벤트 줄을 확인한다. 그 요청은 본문에 다른 요청이 없다(`replay/_boundary.py`).
- `redirect.denied.not_allowed`의 reason에는 규칙 번호만 넣는다. 파싱한 호스트의 해시를 넣을지는 작성자 결정 대기(수업 계획 11-2 검토 포인트).
- `config.audit.failed`는 7장의 도구 이벤트와 같은 길(`tools/secevent.py`)을 쓴다. CI 안에서는 작업 로그가 기록이다.

## 12장: 연동 앱과 파트너

- 12장 이벤트는 모두 mall-portal(파트너 입구 A9 뒤)이 남긴다. `partner.app.changed`만 mall-admin(대장·일괄 폐기)이 남긴다. actor는 `partner:<앱 ID>`(토큰의 partner_id), 관리자 변경은 `admin:<계정>`.
- 토큰·비밀값·인증서 원문과 지문 값은 넣지 않는다. 내보내기 이벤트에는 건수만(응답 본문을 로그에 남기면 로그가 또 하나의 유출 통로가 된다).
- `partner.export`의 `count`는 내보낸 행 수다(다른 이벤트처럼 「최근 10분 횟수」가 아니다). 경보는 `make alert-rules`가 만들지 않고 손으로 쓴 `infra/observability/rules/partner.yaml`(12-4)이 맡는다.
- 배정 밖 주문 404는 새 이름을 만들지 않고 2-2의 `access.denied.not_owner`를 쓴다(소유는 조회 조건이 답한다는 같은 이야기). 경보 규칙은 2-2 줄을 따른다.
- `partner.denied.bad_credential`은 수업 계획(12-2)의 이벤트 목록에 없던 것이다. 비밀값 회전 겹침이 끝난 뒤 옛 값으로 오는 파트너를 찾는 데 쓴다(작성자 확인 대기).
- Express diff(`diffs/s12-0*`)도 같은 이름을 쓴다(`kit.partnerScope`).

## 13장: 고액 승인과 사람

- 13장 이벤트의 대부분은 **승인 서비스(approval, A10)**가 남긴다. 승인 서비스는 mall 코드를 가져오지 않으므로 같은 형식을 `infra/approval/.../SecurityEvents.java`가 다시 쓴다(로거 `SECURITY`, 같은 키). `make logs S=approval | grep SECURITY`, `make lab`은 a10이 켜져 있으면 승인 서비스 줄도 읽는다.
- 관리자 앱(mall-admin)이 남기는 것은 `refund.denied.no_approval`(실행기)·`auth.denied.disabled_account`·`ops.denied.no_elevation`·`ops.elevation.granted`다.
- **같은 trace_id가 mall-admin 줄과 approval 줄에 함께 남는다.** 관리자 앱이 받은 `X-Trace-Id`를 승인 서비스 요청 헤더로 그대로 넘긴다(랩-설계 11절, 14-4 타임라인).
- 금액·확인 번호·승인 토큰은 넣지 않는다(작성자 검토 포인트 S13-2). target은 `return:<ID>`·`approval:<ID>`·`admin:<계정>`·`path:<경로>`만. 금액이 필요하면 승인 서비스 DB(`daon_approval_request`)를 따로 본다.
- `approval.denied.bad_confirm`은 수업 계획(13-4) 목록에 없던 것이다. 메일 확인 번호 맞혀 보기를 본다(작성자 확인 대기).
- 정상 승인·실행은 보안 이벤트가 아니라 승인 서비스 표(`daon_approval_request`)와 관리자 앱 일반 로그에 남는다. 거부·의심만 SECURITY 줄이다(8-3 감사 로그와의 차이).
- Express diff(`diffs/s13-0*`)도 같은 이름을 쓴다(`kit.requiresApproval`).

## 14장: 사고가 났을 때

- 14장은 **새 공격 징후 로그를 거의 만들지 않는다.** 14-4 타임라인(`python -m daon timeline`)은 2~13장 줄과 `trace_id`를 모아 쓴다. 새로 심은 것은 백업(14-2)·축소 운영(14-3)·격리(14-4) 여섯 가지다.
- `backup.*`는 앱이 아니라 **backup-audit**(백업 저장소의 감사 로그 webhook을 받는 작은 서버)가 남긴다. MinIO는 SecurityEventLogger를 쓰지 못해서다. 줄 모양은 Express와 같은 `WARN SECURITY {…}`이고 수집기(A5)가 stream=security로 모은다. `make logs S=backup-audit | grep SECURITY`.
- `backup.denied.delete`는 거부된 지우기만 남긴다. 보존 기간 안의 버전은 지울 수 없고, 버전 없는 지우기(삭제 표시)는 지우기 권한이 있는 계정만 할 수 있는데 백업 저장소에는 그런 사용자가 없다(루트만, 루트는 backup-init만 쓴다).
- `backup.restore.drill`은 도구 이벤트다(7장 `supply.*`와 같은 길). 숫자(RTO·RPO)가 점점 나빠지는지를 사람이 본다. 경보 규칙은 만들지 않는다.
- `ops.mode.degraded`는 거부가 아니지만 사고 선언 후보라 WARN이다. 끌 때는 SECURITY 줄을 남기지 않고 감사 로그(`ops.mode.set`)에만 남긴다. 축소 운영이 여는 출금 서킷 브레이커는 13-4의 `payout.breaker.open`(lesson 14-3, reason 「사람이 멈춤」)을 다시 쓴다. 느린 호출로 열린 결제 확인 브레이커는 9-5의 `resilience.circuit.opened`(lesson 14-3)를 다시 쓴다.
- 14-4 격리는 이름을 새로 만들지 않은 줄이 둘이다: 잠긴 회원 로그인 `auth.denied.disabled_account`(13-6 이름), 격리 전 토큰 재사용 `auth.token.revoked_used`(4-8 이름). 경보 규칙은 처음 줄을 따른다.
- 모의 침해(`make drill D=14-4`)의 요청은 `trace_id`가 `drill-14-4-`로 시작한다. 실제 사고 줄과 섞이지 않게 타임라인에서 `--trace-prefix`로 고른다.

## 15장: 최종 점검

- 15장은 **새 공격 징후 로그를 거의 만들지 않는다.** 캡스톤(`make capstone`)은 2~14장에서 심은 줄과 `trace_id`(`cap-<단계>-<순번>`)로 사고를 다시 세운다.
- 15장 시작 모습: 관리자의 받는 사람 정보 수정이 **감사 로그**(SECURITY 줄이 아니다)에 남는다. action `change.order.receiver`, target `order:<ID>`(본문의 orderId, `@Audited(idField = "orderId")`), count 1. 바뀐 값은 넣지 않는다. 이름을 `order.*`로 짓지 않은 이유는 8-4 대량 조회 규칙(`rules/bulk.yaml`)이 `order.*`의 count를 읽은 건수로 더하기 때문이다.
- 배송지 변경 경보 `data.change.receiver_bulk`(15-2)는 SECURITY 이벤트가 아니라 감사 로그 합계라 손으로 쓴 `infra/observability/rules/change.yaml`에 둔다(`make alert-rules`가 만들지 않는다).
- 정상 로그인은 SECURITY 줄을 남기지 않는다. 15-1의 「새 곳 로그인」은 원본 로그인 기록 표(`ums_admin_login_log`)로 본다: `python -m daon timeline --actor admin:<계정> --logins`.

