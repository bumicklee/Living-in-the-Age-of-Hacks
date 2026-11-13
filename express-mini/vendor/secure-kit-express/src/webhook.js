// 부품 20 idempotency(웹훅) · kit.webhook()  (수업 10-4)
// 결제사 웹훅 받는 곳에 한 줄로 끼운다(②). bodyLimit()이 먼저 원본 바이트(req.rawBody)를 읽어 두어야 한다.
// 1) 서명: X-Signature: sha256=<hex(HMAC-SHA256(비밀값, "<X-Timestamp>.<본문 바이트>"))>  틀리면 401
// 2) 시각: X-Timestamp(유닉스 초)가 지금에서 5분 넘게 벗어나면 401(다시 보내기 공격)
// 3) 중복: 같은 eventId는 한 번만. 다시 오면 200 { duplicate: true } (결제사는 200을 받아야 재전송을 멈춘다)
// 설정: webhook: { secret, tolerance: '5m', 'id-field': 'eventId', 'event-prefix': 'payment' }
import { createHmac, timingSafeEqual } from 'node:crypto';
import { safeTarget } from './events.js';
import { onResponseEnd, readBody, sendError, sendJson } from './http.js';
import { parseDuration } from './config.js';

const PART = 'idempotency';

export function createWebhook({ events, store, cfg = {}, failOpen = false, enabled = true }) {
  const secret = cfg.secret;
  if (!secret) throw new Error('secure-kit: webhook.secret 설정이 필요합니다');
  const toleranceS = parseDuration(cfg.tolerance, 300_000) / 1000;
  const idField = cfg['id-field'] ?? 'eventId';
  const prefix = cfg['event-prefix'] ?? 'payment';
  const keepMs = parseDuration(cfg['dedupe-ttl'], 7 * 86_400_000);

  function validSignature(ts, raw, given) {
    const want = Buffer.from(createHmac('sha256', secret).update(`${ts}.`).update(raw).digest('hex'), 'utf8');
    const got = Buffer.from(String(given ?? '').replace(/^sha256=/, ''), 'utf8');
    return got.length === want.length && timingSafeEqual(got, want);
  }

  return async function webhook(req, res, next) {
    if (!enabled) return next();
    const raw = req.rawBody ?? (await readBody(req, 1024 * 1024)).buffer ?? Buffer.alloc(0);
    const ts = String(req.headers['x-timestamp'] ?? '');
    const fresh = /^\d{1,12}$/.test(ts) && Math.abs(Date.now() / 1000 - Number(ts)) <= toleranceS;
    if (!fresh || !validSignature(ts, raw, req.headers['x-signature'])) {
      events.emit(req, { event: `${prefix}.denied.bad_signature`, part: PART, target: 'webhook:signature', reason: fresh ? '서명 불일치' : '시각이 허용 범위 밖' });
      return sendError(req, res, 401, '서명을 확인할 수 없습니다');
    }
    let body = req.body;
    if (!body || typeof body !== 'object' || !(idField in body)) {
      try {
        body = JSON.parse(raw.toString('utf8'));
        req.body = body;
      } catch {
        return sendError(req, res, 400, '잘못된 요청입니다');
      }
    }
    const id = body?.[idField];
    if (typeof id !== 'string' && typeof id !== 'number') return sendError(req, res, 400, `${idField}가 필요합니다`);
    const key = `webhook:${prefix}:${id}`;
    try {
      if (!(await store.setIfAbsent(key, 1, keepMs))) {
        events.emit(req, { event: `${prefix}.duplicate.event`, part: PART, level: 'INFO', target: safeTarget('event', id), reason: '같은 이벤트 다시 받음' });
        return sendJson(res, 200, { duplicate: true });
      }
    } catch {
      if (!failOpen) return sendError(req, res, 503, '잠시 후 다시 시도하세요');
    }
    // 처리하다 서버 오류가 나면 표시를 지워 결제사가 다시 보낼 때 처리되게 한다
    onResponseEnd(res, (status) => {
      if (status >= 500) Promise.resolve(store.del(key)).catch(() => {});
    });
    next();
  };
}
