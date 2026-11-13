// 부품 20 idempotency(요청 중복) · kit.idempotent()  (수업 10-4)
// 환불·결제처럼 두 번 하면 안 되는 곳에 한 줄로 끼운다(②).
//   router.post('/refund', kit.idempotent({ header: 'Idempotency-Key', ttl: '24h' }), refund)
// - 키 없음 → 400  · 같은 키 + 같은 본문 → 처음 응답을 그대로 다시 보낸다(처리는 한 번)
// - 같은 키 + 다른 본문 → 422  · 같은 키가 아직 처리 중 → 409
// 동시성(같은 주문을 서로 다른 키로 두 번)은 막지 않는다. 그것은 DB 조건부 UPDATE·유니크 제약이 막는다.
import { createHash } from 'node:crypto';
import { actorOf, onResponseEnd, sendError, sendJson } from './http.js';
import { parseDuration, stableStringify } from './config.js';

const PART = 'idempotency';

export function createIdempotent({ events, store, opts = {}, failOpen = false, enabled = true }) {
  const header = (opts.header ?? 'Idempotency-Key').toLowerCase();
  const ttlMs = parseDuration(opts.ttl, 24 * 3_600_000);

  const fingerprint = (req) => createHash('sha256')
    .update(`${req.method} ${(req.originalUrl ?? req.url).split('?')[0]}\n`)
    .update(req.body !== undefined && Object.keys(req.body ?? {}).length ? stableStringify(req.body) : req.rawBody ?? '')
    .digest('hex');

  return async function idempotent(req, res, next) {
    if (!enabled) return next();
    const key = req.headers[header];
    if (typeof key !== 'string' || !/^[A-Za-z0-9._:-]{8,128}$/.test(key)) {
      events.emit(req, { event: 'idempotency.denied.missing_key', part: PART, level: 'INFO', reason: '멱등 키 없음·형식 오류' });
      return sendError(req, res, 400, `${opts.header ?? 'Idempotency-Key'} 머리가 필요합니다`);
    }
    // 사람마다 키 공간을 나눈다(남의 키로 남의 응답을 받아 가지 못하게)
    const storeKey = `idem:${createHash('sha256').update(`${actorOf(req)}|${key}`).digest('hex')}`;
    const fp = fingerprint(req);
    try {
      const fresh = await store.setIfAbsent(storeKey, { fp, state: 'pending' }, ttlMs);
      if (!fresh) {
        const rec = await store.get(storeKey);
        if (rec?.fp !== fp) {
          events.emit(req, { event: 'idempotency.denied.key_reuse', part: PART, reason: '같은 키에 다른 본문' });
          return sendError(req, res, 422, '같은 키로 다른 요청을 보냈습니다');
        }
        if (rec.state !== 'done') return sendError(req, res, 409, '같은 요청을 처리하고 있습니다');
        events.emit(req, { event: 'idempotency.replayed', part: PART, level: 'INFO', reason: '같은 키 다시 받음(처음 응답 재전송)' });
        res.setHeader('Idempotent-Replayed', 'true');
        return sendJson(res, rec.status, rec.body);
      }
    } catch {
      if (!failOpen) return sendError(req, res, 503, '잠시 후 다시 시도하세요');
      return next();
    }
    // 처음 응답을 저장한다. 서버 오류(5xx)는 저장하지 않고 키를 지워 다시 시도할 수 있게 한다
    let body;
    const json = res.json?.bind(res);
    if (json) res.json = (b) => { body = b; return json(b); };
    onResponseEnd(res, (status) => {
      const p = status >= 500 ? store.del(storeKey) : store.set(storeKey, { fp, state: 'done', status, body: body ?? null }, ttlMs);
      Promise.resolve(p).catch(() => {});
    });
    next();
  };
}
