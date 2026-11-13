// 부품 15 audit · kit.audited('member.read')  (수업 8-3)
// 개인정보를 보는 곳에 한 줄로 끼운다(②). 누가·무엇을·몇 건·왜 봤는지를 응답이 끝난 뒤 한 줄 남긴다.
// 기록은 정확히 8개 키: { ts, trace_id, actor, action, target, count, reason, result }
//   target: ID만(기본 '<action 앞부분>:<req.params.id>'), count: 응답에 담긴 건수, reason: 머리 X-Access-Reason(업무 번호)
//   result: ok(2xx·3xx) · denied(401·403) · error(그 밖)  · 거부·오류면 count 0
// 값(전화번호 등)은 기록에 넣지 않는다. 로그에는 `INFO AUDIT {json}`, 랩에서는 최근 200개를 /_kit/audit 로 본다.
import { actorOf, onResponseEnd, traceId } from './http.js';

const KEEP_RECORDS = 200;

export function createAuditTrail(logger, sink) {
  const records = [];
  function write(record) {
    records.push(record);
    if (records.length > KEEP_RECORDS) records.shift();
    logger.raw('INFO', `AUDIT ${JSON.stringify(record)}`);
    if (sink) Promise.resolve(sink(record)).catch(() => logger.warn('감사 기록 저장 실패(sink)'));
  }
  return { write, recent: () => [...records] };
}

/** 응답 본문에서 건수를 센다: 배열 길이, { list|items|records: [] } 의 길이, 객체 하나면 1 */
export function countOf(body) {
  if (Array.isArray(body)) return body.length;
  if (body && typeof body === 'object') {
    for (const k of ['list', 'items', 'records', 'content']) if (Array.isArray(body[k])) return body[k].length;
    return 1;
  }
  return 0;
}

/**
 * @param action          'member.read' 처럼 <대상>.<동작>
 * @param opts.target     (req) => 'member:3'  기본은 '<대상>:<req.params.id>'
 * @param opts.count      (req, body) => 숫자   기본은 countOf(body)
 * @param opts.reasonHeader  기본 'x-access-reason'
 */
export function createAudited({ trail, action, opts = {}, cfg = {}, enabled = true }) {
  const resource = action.split('.')[0];
  const reasonHeader = (opts.reasonHeader ?? cfg['reason-header'] ?? 'x-access-reason').toLowerCase();
  const targetOf = opts.target ?? ((req) => `${resource}:${req.params?.id ?? 'unknown'}`);
  const countFn = opts.count ?? ((req, body) => countOf(body));

  return function audited(req, res, next) {
    if (!enabled) return next();
    traceId(req, res);
    // 응답 본문을 보려고 res.json을 한 번 감싼다(본문은 기록하지 않고 건수만 센다)
    let body;
    const json = res.json?.bind(res);
    if (json) {
      res.json = (b) => {
        body = b;
        return json(b);
      };
    }
    onResponseEnd(res, (s) => {
      const result = s < 400 ? 'ok' : s === 401 || s === 403 ? 'denied' : 'error';
      let count = 0;
      if (result === 'ok') {
        try {
          count = Number(countFn(req, body)) || 0;
        } catch {
          count = 0;
        }
      }
      const reason = req.headers[reasonHeader];
      trail.write({
        ts: new Date().toISOString(),
        trace_id: traceId(req),
        actor: actorOf(req),
        action,
        target: String(targetOf(req)).slice(0, 128),
        count,
        reason: typeof reason === 'string' ? reason.slice(0, 64) : '',
        result
      });
    });
    next();
  };
}
