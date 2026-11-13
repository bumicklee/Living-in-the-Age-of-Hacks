// 부품 6 rate-limit · kit.rateLimit('login')  (수업 4-3)
// 로그인 같은 곳에 한 줄로 끼운다(②). 설정은 이름별로 둔다(①):
//   'rate-limit': { login: { 'per-account': '5/10m', 'per-ip': '50/10m', 'account-field': 'username' } }
// 세는 것은 「실패」(응답 401·403)다. 계정별 실패가 상한에 닿으면, 다음 요청은 비밀번호가 맞아도 429.
// IP는 req.socket.remoteAddress 로 센다(X-Forwarded-For는 믿지 않는다. 프록시 뒤라면 'trust-proxy-header'로 이름을 준다).
import { createHash } from 'node:crypto';
import { safeTarget } from './events.js';
import { onResponseEnd, sendError } from './http.js';
import { parseRate } from './config.js';

const PART = 'rate-limit';
const FAIL_STATUS = new Set([401, 403]);

export function createRateLimit({ events, store, name, cfg = {}, failOpen = false, enabled = true }) {
  const perAccount = parseRate(cfg['per-account']);
  const perIp = parseRate(cfg['per-ip']);
  if (!perAccount && !perIp) throw new Error(`secure-kit: rate-limit.${name} 설정이 없습니다(per-account 또는 per-ip)`);
  const field = cfg['account-field'] ?? 'username';
  const ipHeader = cfg['trust-proxy-header'];

  const ipOf = (req) => {
    const h = ipHeader && req.headers[ipHeader.toLowerCase()];
    return (typeof h === 'string' && h.split(',')[0].trim()) || req.socket?.remoteAddress || 'unknown';
  };
  // 저장소 키에는 아이디 원문 대신 해시를 쓴다(Redis 키 목록에 아이디가 줄줄이 보이지 않게)
  const keyOf = (kind, value) => `rl:${name}:${kind}:${createHash('sha256').update(String(value)).digest('hex').slice(0, 32)}`;

  async function overLimit(key, rule) {
    if (!rule) return null;
    const n = await store.get(key);
    return n && n.count >= rule.limit ? n : null;
  }

  async function countFailure(key, rule) {
    if (!rule) return;
    const cur = await store.get(key);
    if (!cur) return store.set(key, { count: 1, until: Date.now() + rule.windowMs }, rule.windowMs);
    // 첫 실패부터 창을 잰다(고정 창). 남은 시간을 그대로 이어 간다
    const left = Math.max(1, cur.until - Date.now());
    return store.set(key, { count: cur.count + 1, until: cur.until }, left);
  }

  return async function rateLimit(req, res, next) {
    if (!enabled) return next();
    const account = req.body?.[field];
    const accountKey = typeof account === 'string' && account ? keyOf('account', account.toLowerCase()) : null;
    const ipKey = keyOf('ip', ipOf(req));
    try {
      const hit = (accountKey && (await overLimit(accountKey, perAccount))) || (await overLimit(ipKey, perIp));
      if (hit) {
        const retryAfter = Math.max(1, Math.ceil((hit.until - Date.now()) / 1000));
        events.emit(req, {
          event: cfg.event ?? `auth.${name}.rate_limited`, // 이름을 바꾸려면 'event' 설정
          part: PART,
          target: accountKey ? safeTarget('account', account) : safeTarget('ip', ipOf(req)),
          reason: '실패 횟수 상한'
        });
        return sendError(req, res, 429, '잠시 후 다시 시도하세요', {}, { 'Retry-After': String(retryAfter) });
      }
    } catch (err) {
      if (!failOpen) {
        events.emit(req, { event: 'kit.fail_closed.store_error', part: PART, reason: '저장소 오류로 막음' });
        return sendError(req, res, 503, '잠시 후 다시 시도하세요');
      }
    }
    // 처리 함수가 응답을 끝낼 때 실패(401·403)면 센다
    onResponseEnd(res, (status) => {
      if (!FAIL_STATUS.has(status)) return;
      Promise.all([accountKey && countFailure(accountKey, perAccount), countFailure(ipKey, perIp)]).catch(() => {});
    });
    next();
  };
}
