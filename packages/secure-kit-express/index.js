// @secure-kit/express — 막을 내용 하나 = 부품 하나. 필요한 것만 골라 끼운다. MIT.
//   import { secureKit } from '@secure-kit/express';
//   const kit = secureKit(config);   // 설정 키는 Spring application.yml 의 secure-kit.* 와 같다
//   app.use(kit.headers(), kit.bodyLimit());
//   router.post('/login', kit.rateLimit('login'), login);
//   app.use(kit.errorResponse());     // 오류 처리기는 맨 끝
// 부품끼리 같이 쓰는 것(이벤트·로거·저장소·감사 기록)은 kit 하나에 하나씩만 만든다.
import { flatten, isEnabled, isFailOpen, partConfig, passThrough, pick } from './src/config.js';
import { MemoryStore, assertStore } from './src/store.js';
import { createLogger } from './src/logger.js';
import { createEvents } from './src/events.js';
import { wrapAsync } from './src/http.js';
import { createSafeFetch } from './src/safeFetch.js';
import { createHostAllowlist } from './src/hostAllowlist.js';
import { createSafeRedirect } from './src/safeRedirect.js';
import { createUpload } from './src/upload.js';
import { createHeaders } from './src/headers.js';
import { createRateLimit } from './src/rateLimit.js';
import { createPasswordPolicy } from './src/passwordPolicy.js';
import { createConstantTimeLogin } from './src/constantTimeLogin.js';
import { createResetLink } from './src/resetLink.js';
import { createRequireStepUp } from './src/requireStepUp.js';
import { createToken } from './src/token.js';
import { createFieldCrypto } from './src/fieldCrypto.js';
import { createStartupCheck } from './src/startupCheck.js';
import { createErrorResponse, createNotFound } from './src/errorResponse.js';
import { createAuditTrail, createAudited } from './src/audited.js';
import { createBodyLimit } from './src/bodyLimit.js';
import { createSafeRegex } from './src/safeRegex.js';
import { createFailClosed } from './src/failClosed.js';
import { createResilientFetch, createResilientRegistry } from './src/resilientFetch.js';
import { createIdempotent } from './src/idempotent.js';
import { createWebhook } from './src/webhook.js';
import { createBudget } from './src/budget.js';
import { createNoStoreAuthenticated } from './src/noStoreAuthenticated.js';
import { createPartnerScope } from './src/partnerScope.js';
import { createApprovals, createRequiresApproval } from './src/requiresApproval.js';
import { createLab } from './src/lab.js';

export { MemoryStore } from './src/store.js';
export { KitHttpError, traceId, actorOf, sendError } from './src/http.js';
export { hashPassword, verifyPassword } from './src/constantTimeLogin.js';
export { maskPhone, maskText } from './src/logger.js';
export { isInternalAddress } from './src/safeFetch.js';

/**
 * @param config   secure-kit 설정. { 'secure-kit': {...} } 로 감싸도, 안쪽만 넘겨도 된다
 * @param options  { store, mailer, auditSink, logWrite }  — store를 Redis 구현으로 바꾸면 서버 여러 대에서 같이 센다
 */
export function secureKit(config = {}, options = {}) {
  const cfg = config['secure-kit'] ?? config;
  const logger = createLogger({ ...partConfig(cfg, 'log-masking'), write: options.logWrite });
  const events = createEvents(logger);
  const store = assertStore(options.store ?? new MemoryStore());
  const outbox = [];
  const auditTrail = createAuditTrail(logger, options.auditSink);
  const resilient = createResilientRegistry();
  const resetHooks = [];
  const approvals = createApprovals({ events, store });
  const on = (key) => isEnabled(cfg, key);
  const failOpen = (key) => isFailOpen(cfg, key);
  const once = (make) => { let v; return () => (v ??= make()); };

  const startupCheck = once(() => createStartupCheck({ events, cfg: partConfig(cfg, 'startup-check') }));
  const token = once(() => createToken({ events, store, cfg: partConfig(cfg, 'token') }));
  const fieldCrypto = once(() => createFieldCrypto({ events, cfg: partConfig(cfg, 'field-crypto') }));
  const send = options.mailer ?? (async (mail) => { outbox.push(mail); if (outbox.length > 50) outbox.shift(); });

  const kit = {
    config: cfg,
    store,
    events,
    auditTrail,
    approvals,
    outbox,
    logger: () => logger,

    // 1~5
    safeFetch: () => createSafeFetch({ events, cfg: partConfig(cfg, 'safe-http-client') }),
    hostAllowlist: () => (on('host-allowlist') ? createHostAllowlist({ events, raw: pick(cfg, 'host-allowlist', []) }) : passThrough),
    safeRedirect: () => createSafeRedirect({ events, cfg: partConfig(cfg, 'safe-redirect') }),
    upload: () => (on('upload') ? createUpload({ events, cfg: partConfig(cfg, 'upload') }) : passThrough),
    headers: () => createHeaders({ events, cfg: partConfig(cfg, 'headers'), cors: partConfig(cfg, 'cors'), enabled: on('headers') }),
    // 6~10
    rateLimit: (name) => wrapAsync(createRateLimit({ events, store, name, cfg: partConfig(cfg, `rate-limit.${name}`), failOpen: failOpen('rate-limit'), enabled: on('rate-limit') })),
    passwordPolicy: (opts = {}) => createPasswordPolicy({ events, cfg: partConfig(cfg, 'password-policy'), breachedCheck: opts.breachedCheck }),
    constantTimeLogin: (opts) => createConstantTimeLogin({ ...opts, iterations: opts?.iterations ?? pick(cfg, 'constant-time-login.iterations', undefined) }),
    resetLink: () => createResetLink({ events, store, cfg: partConfig(cfg, 'reset-link'), send }),
    requireStepUp: (opts) => wrapAsync(createRequireStepUp({ events, cfg: partConfig(cfg, 'step-up'), ...opts, enabled: on('step-up') })),
    // 11~15
    token,
    fieldCrypto,
    startupCheck,
    errorResponse: () => createErrorResponse({ logger, cfg: partConfig(cfg, 'error-response'), enabled: on('error-response') }),
    notFound: () => createNotFound(),
    audited: (action, opts) => createAudited({ trail: auditTrail, action, opts, cfg: partConfig(cfg, 'audit'), enabled: on('audit') }),
    // 16~20 (logger는 위)
    bodyLimit: () => wrapAsync(createBodyLimit({ events, cfg: partConfig(cfg, 'limits'), enabled: on('limits') })),
    safeRegex: (pattern, opts) => createSafeRegex({ events, pattern, opts }),
    failClosed: (check, opts) => wrapAsync(createFailClosed({ events, check, opts, failOpen: failOpen('fail-closed'), enabled: on('fail-closed') })),
    resilientFetch: (name = 'default', opts = {}) => createResilientFetch({ events, registry: resilient, name, cfg: partConfig(cfg, 'resilient-client'), ...opts }),
    idempotent: (opts) => wrapAsync(createIdempotent({ events, store, opts, failOpen: failOpen('idempotency'), enabled: on('idempotency') })),
    webhook: () => wrapAsync(createWebhook({ events, store, cfg: partConfig(cfg, 'webhook'), failOpen: failOpen('webhook'), enabled: on('webhook') })),
    // 21~24
    budget: (name, opts) => wrapAsync(createBudget({ events, store, name, cfg: partConfig(cfg, `budget.${name}`), opts, failOpen: failOpen('budget'), enabled: on('budget') })),
    noStoreAuthenticated: () => createNoStoreAuthenticated({ cfg: partConfig(cfg, 'cache-control'), enabled: on('cache-control') }),
    partnerScope: (scope, opts) => createPartnerScope({ events, token: token(), scope, opts, enabled: on('partner-scope') }),
    requiresApproval: (policy, opts) => wrapAsync(createRequiresApproval({ events, approvals, policy, cfg: partConfig(cfg, 'approval'), opts, enabled: on('approval') })),

    // 랩 전용
    lab: (opts = {}) => wrapAsync(createLab({ kit, expose: pick(cfg, 'lab.expose', false) === true, routes: opts.routes })),
    /** 랩 /_kit/reset: 메모리 상태를 처음으로. 이벤트·감사 기록은 지우지 않는다 */
    async reset() {
      await store.clear();
      resilient.reset();
      outbox.length = 0;
      for (const fn of resetHooks) await fn();
    },
    onReset: (fn) => resetHooks.push(fn)
  };

  // 부품 13: kit 자기 설정부터 검사한다(걸리면 여기서 throw → 앱이 뜨지 않는다)
  if (on('startup-check')) startupCheck().assert(flatten(cfg, 'secure-kit'));
  return kit;
}

export default secureKit;
