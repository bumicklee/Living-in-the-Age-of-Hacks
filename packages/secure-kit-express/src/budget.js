// 부품 21 budget · kit.budget('sms')  (수업 10-7)
// 문자 발송·외부 유료 API처럼 「부를 때마다 돈이 드는」 곳에 한 줄로 끼운다(②). 대상(전화번호·사용자)별 횟수를 센다.
//   router.post('/sms', kit.budget('sms', { key: (req) => req.body.phone, targetType: 'phone' }), sendSms)
// 설정: budget: { sms: { 'per-target': '1/60s', 'per-actor': '20/1h' } }
// 넘으면 429 + budget.denied.<이름>(target '<종류>:<sha256 앞 8자>', 번호 원문은 남기지 않는다)
import { createHash } from 'node:crypto';
import { actorOf, sendError } from './http.js';
import { parseRate } from './config.js';

const PART = 'budget';
const sha = (v) => createHash('sha256').update(String(v)).digest('hex');
const sha8 = (v) => sha(v).slice(0, 8);

export function createBudget({ events, store, name, cfg = {}, opts = {}, failOpen = false, enabled = true }) {
  const perTarget = parseRate(cfg['per-target']);
  const perActor = parseRate(cfg['per-actor']);
  if (!perTarget && !perActor) throw new Error(`secure-kit: budget.${name} 설정이 없습니다(per-target 또는 per-actor)`);
  const keyOf = opts.key ?? (() => null);
  const targetType = opts.targetType ?? 'target';

  async function spend(key, rule) {
    if (!rule || key == null) return null;
    const { count, ttlMs } = await store.incr(key, rule.windowMs);
    return count > rule.limit ? Math.max(1, Math.ceil(ttlMs / 1000)) : null;
  }

  return async function budget(req, res, next) {
    if (!enabled) return next();
    const raw = keyOf(req);
    const target = raw == null || raw === '' ? null : String(raw);
    let retryAfter = null;
    let which = '';
    try {
      // 대상 해시만 키로 쓴다(저장소에도 번호 원문을 두지 않는다)
      retryAfter = await spend(target && `budget:${name}:t:${sha(target).slice(0, 32)}`, perTarget);
      which = retryAfter ? 'target' : '';
      if (!retryAfter) {
        retryAfter = await spend(`budget:${name}:a:${sha8(actorOf(req))}`, perActor);
        which = retryAfter ? 'actor' : '';
      }
    } catch {
      if (!failOpen) {
        events.emit(req, { event: 'kit.fail_closed.store_error', part: PART, reason: '저장소 오류로 막음' });
        return sendError(req, res, 503, '잠시 후 다시 시도하세요');
      }
      return next();
    }
    if (!retryAfter) return next();
    events.emit(req, {
      event: `budget.denied.${name}`,
      part: PART,
      target: which === 'target' ? `${targetType}:${sha8(target)}` : `actor:${sha8(actorOf(req))}`,
      reason: which === 'target' ? '대상별 발송 예산 초과' : '사용자별 예산 초과'
    });
    sendError(req, res, 429, '잠시 후 다시 시도하세요', {}, { 'Retry-After': String(retryAfter) });
  };
}
