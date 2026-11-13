// 「막으면 남긴다」: 보안 이벤트 한 줄. 모양(키 고정, CONTRACT 0절)
// { seq, event, part, level, actor, target, reason, ts, trace_id }
// 로그에는 `WARN SECURITY {json}` 한 줄(8장 수집기가 그대로 읽는다), 랩에서는 최근 500개를 /_kit/events 로 본다.
import { actorOf, traceId } from './http.js';

const KEEP_EVENTS = 500;

export function createEvents(logger) {
  let seq = 0;
  const ring = [];

  /**
   * @param req    요청(없으면 추적 번호를 새로 만들고 actor는 anonymous)
   * @param e      { event, part, level?, target?, reason?, actor? } — target은 ID만, reason에는 값을 넣지 않는다
   */
  function emit(req, e) {
    seq += 1;
    const record = {
      seq,
      event: e.event,
      part: e.part,
      level: e.level === 'INFO' ? 'INFO' : 'WARN',
      actor: e.actor ?? actorOf(req),
      target: e.target ?? '',
      reason: e.reason ?? '',
      ts: new Date().toISOString(),
      trace_id: req ? traceId(req) : 'startup'
    };
    ring.push(record);
    if (ring.length > KEEP_EVENTS) ring.shift();
    logger.raw(record.level, `SECURITY ${JSON.stringify(record)}`);
    return record;
  }

  return {
    emit,
    /** after보다 큰 seq만 */
    after: (after = 0) => ({ seq, events: ring.filter((x) => x.seq > after) }),
    lastSeq: () => seq
  };
}

/** target 값이 길거나 줄바꿈을 품으면 로그 주입이 된다. 64자까지, 보이는 글자만 */
export function safeTarget(prefix, value, max = 64) {
  const v = String(value ?? '').replace(/[^\x21-\x7e]/g, '?').slice(0, max);
  return `${prefix}:${v}`;
}
