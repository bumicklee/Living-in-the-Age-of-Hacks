// express-kit-demo: secure-kit 부품 24개를 「얇은 처리 함수 + 부품 한 줄」로 끼운 예제.
// 주소·응답은 packages/contract-tests/CONTRACT.md 1절과 같다(Spring 예제와 같은 시험이 돈다). 모든 값은 가짜다.
import express from 'express';
import { maskPhone, secureKit, sendError } from '@secure-kit/express';
import { demoUsers, kitConfig, members } from './config.js';

const LOGIN_FAILED = '아이디 또는 비밀번호가 올바르지 않습니다';
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

export function createApp({ port = 18182 } = {}) {
  const kit = secureKit(kitConfig); // 기동 검사(부품 13)가 여기서 돈다
  const log = kit.logger();
  const app = express();
  app.disable('x-powered-by');
  app.locals.kit = kit;

  // ── 앱 상태(메모리. /_kit/reset 이 데모 사용자 비밀번호를 처음으로 되돌린다) ──
  let users = demoUsers.map((u) => ({ ...u }));
  kit.onReset(() => { users = demoUsers.map((u) => ({ ...u })); });
  const findUser = async (name) => users.find((u) => u.sub === name) ?? null;
  const counts = { refund: 0, webhook: 0, flaky: 0 };
  const pii = new Map();
  let piiSeq = 0;

  // ── 부품 준비 ──
  const login = kit.constantTimeLogin({ findUser });
  const tokens = kit.token();
  const auth = tokens.authenticate();
  const policy = kit.passwordPolicy();
  const resetLink = kit.resetLink();
  const redirect = kit.safeRedirect();
  const safeFetch = kit.safeFetch();
  const crypto = kit.fieldCrypto();
  const coupon = kit.safeRegex('^[A-Z0-9]+(-[A-Z0-9]+)*$', { maxLength: 20, name: 'coupon' });
  const flakyUrl = process.env.KIT_FLAKY_URL || `http://127.0.0.1:${port}/_kit/flaky`;
  const callFlaky = kit.resilientFetch('flaky');
  // 역할 확인은 부품이 아니다(앱의 권한 규칙). 감사 기록이 거부도 남기도록 audited 뒤에 둔다
  const requireRole = (role) => (req, res, next) => (req.secureKitUser?.roles.includes(role) ? next() : sendError(req, res, 403, '권한이 없습니다'));

  // ── 모든 요청에 ──
  app.use(kit.headers()); // 5 보안 헤더·CORS·X-Trace-Id
  app.use(kit.hostAllowlist()); // 2 낯선 Host
  app.use(kit.noStoreAuthenticated()); // 22 인증 응답 no-store
  app.use(kit.bodyLimit()); // 17 본문 상한(1MB, /kit/upload 3MB) + JSON 풀기
  app.use(kit.lab({ routes: {
    'GET /_kit/pii/raw/:id': (req, res, { params }) => {
      const row = pii.get(Number(params.id));
      return row ? res.json({ raw: row.phone }) : sendError(req, res, 404, '찾을 수 없습니다');
    },
    'GET /_kit/flaky': (req, res) => { counts.flaky += 1; sendError(req, res, 503, '잠시 후 다시 시도하세요'); },
    'GET /_kit/flaky/count': (req, res) => res.json({ count: counts.flaky }),
    'POST /_kit/flaky/reset': (req, res) => { counts.flaky = 0; res.status(204).end(); },
    'GET /_kit/refund/count': (req, res) => res.json({ count: counts.refund }),
    'GET /_kit/webhook/count': (req, res) => res.json({ count: counts.webhook })
  } }));

  const r = express.Router();

  r.get('/kit/public', (req, res) => res.set('Cache-Control', 'public, max-age=60').json({ ok: true }));

  // 1 safe-http-client
  r.get('/kit/fetch', wrap(async (req, res) => {
    const out = await safeFetch(req.query.url, { req });
    res.json({ status: out.status, bytes: out.body.length });
  }));
  // 3 safe-redirect
  r.get('/kit/redirect', (req, res) => redirect.redirect(req, res, req.query.next));
  // 4 upload
  r.post('/kit/upload', kit.upload(), (req, res) => res.status(201).json({ stored: req.upload.stored, type: req.upload.type }));

  // 6 rate-limit + 8 constant-time-login + 11 token
  r.post('/kit/login', kit.rateLimit('login'), wrap(async (req, res) => {
    const user = await login.login(req.body?.username, req.body?.password);
    if (!user) return sendError(req, res, 401, LOGIN_FAILED);
    res.json(tokens.issue({ sub: user.sub, roles: user.roles }));
  }));
  // 7 password-policy
  r.post('/kit/password/check', wrap(async (req, res) => {
    const out = await policy.check(req.body?.password, req);
    res.status(out.ok ? 200 : 422).json(out);
  }));
  // 9 reset-link
  r.post('/kit/reset/request', wrap(async (req, res) => {
    const user = await findUser(req.body?.username);
    await resetLink.issue(req, user ? { id: user.sub, email: user.email } : null);
    res.status(202).json({ message: '안내 메일을 보냈습니다(가입된 경우)' });
  }));
  r.post('/kit/reset/confirm', wrap(async (req, res) => {
    const ok = await policy.check(req.body?.newPassword, req);
    if (!ok.ok) return res.status(422).json(ok);
    const sub = await resetLink.consume(req, req.body?.token);
    const user = await findUser(sub);
    if (user) user.passwordHash = await login.hashPassword(req.body.newPassword);
    res.json({ ok: true });
  }));
  // 10 step-up
  r.post('/kit/account/email', auth, kit.requireStepUp({
    verify: async (req, pw) => login.verifyPassword(pw, (await findUser(req.secureKitUser.sub))?.passwordHash)
  }), (req, res) => res.json({ ok: true }));
  // 11 token · 22 no-store
  r.get('/kit/me', auth, (req, res) => res.json({ sub: req.secureKitUser.sub, roles: req.secureKitUser.roles }));
  r.post('/kit/logout', auth, wrap(async (req, res) => {
    await tokens.revoke(req.secureKitUser.claims);
    res.status(204).end();
  }));

  // 12 field-crypto
  r.post('/kit/pii', auth, (req, res) => {
    piiSeq += 1;
    pii.set(piiSeq, { id: piiSeq, ...crypto.seal({ phone: String(req.body?.phone ?? '') }, ['phone']) });
    res.status(201).json({ id: piiSeq });
  });
  r.get('/kit/pii/search', auth, (req, res) => {
    const h = crypto.blindIndex(String(req.query.phone ?? ''));
    res.json({ ids: [...pii.values()].filter((row) => row.phone_hash === h).map((row) => row.id) });
  });
  r.get('/kit/pii/:id', auth, (req, res) => {
    const row = pii.get(Number(req.params.id));
    if (!row) return sendError(req, res, 404, '찾을 수 없습니다');
    res.json({ phone: crypto.open(row, ['phone'], { mask: true, req }).phone });
  });

  // 14 error-response
  r.get('/kit/error', () => {
    throw new Error("SQLSyntaxErrorException: Unknown column 'x' in 'oms_order'");
  });
  // 15 audit
  r.get('/kit/admin/members', auth, kit.audited('member.list', {
    target: (req) => `member:list?page=${pageOf(req).page}&size=${pageOf(req).size}`
  }), requireRole('admin'), (req, res) => {
    const { page, size } = pageOf(req);
    const list = members.slice((page - 1) * size, page * size).map((m) => ({ id: m.id, name: m.name, phone: maskPhone(m.phone) }));
    res.json({ list, total: members.length });
  });
  // 16 log-masking: 받은 본문을 그대로 한 줄 찍는다(로거가 가린다)
  r.post('/kit/log', (req, res) => {
    log.info('kit.log 받은 본문', req.body);
    res.status(204).end();
  });
  // 17 limits
  r.post('/kit/echo', (req, res) => res.json({ bytes: req.rawBody?.length ?? 0 }));
  r.get('/kit/coupon', (req, res) => res.json({ valid: coupon.test(req, req.query.code) }));
  // 18 fail-closed: 본인 확인 의존성(가짜). dep=down 이면 예외
  r.get('/kit/failclosed', kit.failClosed(async (req) => {
    if (req.query.dep === 'down') throw new Error('본인 확인 서비스 연결 실패(가짜)');
    return true;
  }, { dependency: 'identity' }), (req, res) => res.json({ ok: true }));
  // 19 resilient-client
  r.get('/kit/resilient', wrap(async (req, res) => {
    const dep = await callFlaky(flakyUrl, {}, req);
    res.json({ ok: true, status: dep.status });
  }));
  // 20 idempotency · webhook
  r.post('/kit/refund', kit.idempotent({ header: 'Idempotency-Key', ttl: '24h' }), (req, res) => {
    counts.refund += 1;
    res.json({ refundId: `rf-${counts.refund}-${Date.now().toString(36)}`, orderId: req.body?.orderId, amount: req.body?.amount, status: 'REFUNDED' });
  });
  r.post('/kit/webhook', kit.webhook(), (req, res) => {
    counts.webhook += 1;
    res.json({ processed: true });
  });
  // 21 budget
  r.post('/kit/sms', kit.budget('sms', { key: (req) => req.body?.phone, targetType: 'phone' }), (req, res) => res.status(202).json({ sent: true }));
  // 23 partner-scope
  r.get('/kit/partner/orders', kit.partnerScope('orders:read'), (req, res) => res.json({ orders: [{ id: 7, status: 'SHIPPING' }, { id: 8, status: 'READY' }] }));
  r.get('/kit/partner/export', kit.partnerScope('orders:export'), (req, res) => res.json({ export: [] }));
  // 24 approval
  r.post('/kit/refund/high', auth, requireRole('admin'), kit.requiresApproval('refund.high-value', {
    subject: (req) => ({ orderId: req.body?.orderId, amount: req.body?.amount })
  }), (req, res) => res.json({ status: 'DONE', orderId: req.body?.orderId }));

  app.use(r);
  app.use(kit.notFound());
  app.use(kit.errorResponse()); // 14 맨 끝
  return app;
}

function pageOf(req) {
  const page = Math.max(1, Math.min(1000, Number.parseInt(req.query.pageNum, 10) || 1));
  const size = Math.max(1, Math.min(100, Number.parseInt(req.query.pageSize, 10) || 20));
  return { page, size };
}
