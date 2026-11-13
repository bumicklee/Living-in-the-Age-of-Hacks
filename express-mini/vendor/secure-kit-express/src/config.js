// 설정 읽기 도우미. 키는 Spring application.yml의 secure-kit.* 와 같다(kebab-case 문자열 키).
// 예: { 'rate-limit': { login: { 'per-account': '5/10m' } } }

/** 'rate-limit.login.per-account' 처럼 점으로 이은 경로로 값을 꺼낸다 */
export function pick(config, path, fallback) {
  let cur = config;
  for (const part of path.split('.')) {
    if (cur == null || typeof cur !== 'object' || !(part in cur)) return fallback;
    cur = cur[part];
  }
  return cur === undefined ? fallback : cur;
}

const UNIT_MS = { ms: 1, s: 1000, m: 60_000, h: 3_600_000, d: 86_400_000 };

/** '15m' '24h' '60s' 같은 기간 → 밀리초. 숫자는 초로 본다 */
export function parseDuration(value, fallbackMs) {
  if (value == null) return fallbackMs;
  if (typeof value === 'number') return value * 1000;
  const m = /^(\d+)\s*(ms|s|m|h|d)$/.exec(String(value).trim());
  if (!m) throw new Error(`secure-kit: 기간 형식이 틀렸습니다: ${value}`);
  return Number(m[1]) * UNIT_MS[m[2]];
}

/** '5/10m' → { limit: 5, windowMs: 600000 } */
export function parseRate(value) {
  if (value == null) return null;
  const m = /^(\d+)\s*\/\s*(\w+)$/.exec(String(value).trim());
  if (!m) throw new Error(`secure-kit: 횟수 형식이 틀렸습니다(예: 5/10m): ${value}`);
  return { limit: Number(m[1]), windowMs: parseDuration(m[2]) };
}

const UNIT_BYTES = { b: 1, kb: 1024, mb: 1024 * 1024 };

/** '1mb' '3MB' '512kb' → 바이트. 숫자는 바이트 그대로 */
export function parseSize(value, fallback) {
  if (value == null) return fallback;
  if (typeof value === 'number') return value;
  const m = /^(\d+)\s*(b|kb|mb)$/i.exec(String(value).trim());
  if (!m) throw new Error(`secure-kit: 크기 형식이 틀렸습니다(예: 1mb): ${value}`);
  return Number(m[1]) * UNIT_BYTES[m[2].toLowerCase()];
}

/** 부품 하나의 설정. enabled: false 이면 그 부품은 그냥 통과한다(영상 시연용 스위치) */
export function partConfig(config, key) {
  const c = pick(config, key, {});
  return c && typeof c === 'object' && !Array.isArray(c) ? c : {};
}

export function isEnabled(config, key) {
  return partConfig(config, key).enabled !== false;
}

/** 부품 내부 오류에서도 통과시킬지. 기본은 막는다(fail-closed) */
export function isFailOpen(config, key) {
  return partConfig(config, key)['fail-open'] === true || pick(config, 'fail-open', false) === true;
}

/** { a: { b: 1 } } → { 'prefix.a.b': 1 }. 기동 검사기가 쓴다 */
export function flatten(obj, prefix = '') {
  const out = {};
  for (const [k, v] of Object.entries(obj ?? {})) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) Object.assign(out, flatten(v, key));
    else out[key] = v;
  }
  return out;
}

/** 키 순서와 상관없이 같은 내용이면 같은 문자열(멱등 키·승인 지문에 쓴다) */
export function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map((k) => `${JSON.stringify(k)}:${stableStringify(value[k])}`).join(',')}}`;
  }
  return JSON.stringify(value ?? null);
}

export const passThrough = (req, res, next) => next();
