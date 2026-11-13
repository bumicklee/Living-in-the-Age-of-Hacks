// 부품 19 resilient-client · kit.resilientFetch()  (수업 9-5)
// 다른 서비스를 부를 때 fetch 대신 쓴다. 재시도가 장애를 키우지 않게 기본값을 정해 두었다(①).
// - 재시도: 요청마다 최대 1번(5xx·연결 오류만)
// - 재시도 예산: 최근 1분 새 요청의 10%, 최소 3번. 넘으면 재시도하지 않는다
// - 서킷: 연속 실패 5번이면 20초 열림 → 열린 동안은 부르지 않고 503 + X-Circuit: open. 20초 뒤 한 번 떠보고 성공하면 닫는다
// 설정: 'resilient-client': { retries: 1, 'timeout-ms': 3000,
//         'retry-budget': { ratio: 0.1, 'min-per-minute': 3 }, circuit: { 'failure-threshold': 5, 'open-for': '20s' } }
// 상태는 이 프로세스 메모리(의존성 이름별). 서버가 여러 대면 대마다 따로 센다(그래도 안전한 쪽이다).
import { KitHttpError } from './http.js';
import { parseDuration } from './config.js';

const PART = 'resilient-client';
const MINUTE = 60_000;

export function createResilientRegistry() {
  const states = new Map();
  return {
    stateOf(name) {
      if (!states.has(name)) states.set(name, { requests: [], retries: [], failures: 0, openUntil: 0, halfOpen: false });
      return states.get(name);
    },
    reset: () => states.clear()
  };
}

export function createResilientFetch({ events, registry, cfg = {}, name = 'default', fetchImpl = globalThis.fetch }) {
  const retries = Math.min(1, cfg.retries ?? 1);
  const timeoutMs = cfg['timeout-ms'] ?? 3000;
  const ratio = cfg['retry-budget']?.ratio ?? 0.1;
  const minRetries = cfg['retry-budget']?.['min-per-minute'] ?? 3;
  const threshold = cfg.circuit?.['failure-threshold'] ?? 5;
  const openForMs = parseDuration(cfg.circuit?.['open-for'], 20_000);

  const recent = (list, now) => {
    while (list.length && list[0] <= now - MINUTE) list.shift();
    return list.length;
  };

  function open(s, req) {
    s.openUntil = Date.now() + openForMs;
    s.halfOpen = false;
    events.emit(req, { event: 'resilience.circuit.opened', part: PART, target: `dep:${name}`, reason: `연속 실패 ${s.failures}번` });
  }

  /** 서킷이 열려 있으면 true(부르지 않는다). 열림 시간이 지났으면 한 번만 떠보게 한다 */
  function isOpen(s) {
    if (s.openUntil === 0) return false;
    if (Date.now() < s.openUntil || s.halfOpen) return true;
    s.halfOpen = true; // 이번 한 번은 통과
    return false;
  }

  async function attempt(url, init) {
    try {
      const res = await fetchImpl(url, { ...init, redirect: 'manual', signal: AbortSignal.timeout(timeoutMs) });
      return { res, failed: res.status >= 500 };
    } catch {
      return { res: null, failed: true };
    }
  }

  /** @returns {Promise<Response>} 실패면 KitHttpError(502), 서킷 열림이면 KitHttpError(503, X-Circuit: open) */
  return async function resilientFetch(url, init = {}, req) {
    const s = registry.stateOf(name);
    const now = Date.now();
    recent(s.requests, now);
    s.requests.push(now);
    const circuitOpen = () => new KitHttpError(503, '잠시 후 다시 시도하세요', { headers: { 'X-Circuit': 'open' } });

    for (let i = 0; i <= retries; i += 1) {
      if (isOpen(s)) throw circuitOpen();
      if (i > 0) {
        const budget = Math.max(minRetries, Math.floor(recent(s.requests, Date.now()) * ratio));
        if (recent(s.retries, Date.now()) >= budget) break; // 예산을 다 썼다: 재시도하지 않는다
        s.retries.push(Date.now());
      }
      const { res, failed } = await attempt(url, init);
      if (!failed) {
        s.failures = 0;
        s.openUntil = 0;
        s.halfOpen = false;
        return res;
      }
      s.failures += 1;
      if (s.halfOpen || s.failures >= threshold) {
        open(s, req);
        throw circuitOpen();
      }
    }
    throw new KitHttpError(502, '의존 서비스가 응답하지 않습니다');
  };
}
