// 부품 13 startup-check · kit.startupCheck()  (수업 6-3)
// 기동할 때 「기본값 비밀값」을 찾으면 멈춘다(①). secureKit(config)를 만들 때 kit 자기 설정에도 한 번 돈다.
// 판단: 키 이름이 secret·password·key·token 으로 끝나고, 값이 알려진 기본값이거나 12자 미만이면 거부.
// 값은 어디에도 찍지 않는다(키 이름만).
//   kit.startupCheck().assert({ 'db.password': process.env.DB_PASSWORD, ... })  // 걸리면 throw
import { flatten } from './config.js';

const PART = 'startup-check';
const KNOWN_DEFAULTS = new Set(['changeme', 'change-me', 'secret', 'password', 'root', 'admin', 'minioadmin', 'guest', 'test', '123456', 'macro123']);
const SENSITIVE_END = /(secret|password|key|token)$/i;
const MIN_LENGTH = 12;

export function createStartupCheck({ events, cfg = {} }) {
  const extraDefaults = new Set((cfg['known-defaults'] ?? []).map((v) => String(v).toLowerCase()));
  const minLength = cfg['min-length'] ?? MIN_LENGTH;

  /** 키 이름만 보고 비밀값 칸인지 */
  const isSensitiveKey = (key) => SENSITIVE_END.test(String(key).split('.').pop());

  /**
   * @param props  { 'a.b.c': 값 } 평평한 모양, 또는 중첩 객체(이때는 prefix를 붙여 펼친다)
   * @returns {{ ok: boolean, denied: string[] }}
   */
  function check(props, prefix = '') {
    const flat = Object.values(props ?? {}).some((v) => v && typeof v === 'object' && !Array.isArray(v)) ? flatten(props, prefix) : props ?? {};
    const denied = [];
    for (const [key, value] of Object.entries(flat)) {
      if (!isSensitiveKey(key) || value == null || typeof value === 'object') continue;
      const v = String(value);
      if (KNOWN_DEFAULTS.has(v.toLowerCase()) || extraDefaults.has(v.toLowerCase()) || v.length < minLength) denied.push(key);
    }
    return { ok: denied.length === 0, denied };
  }

  /** 걸리면 이벤트를 남기고 기동을 멈춘다 */
  function assert(props, prefix = '') {
    const r = check(props, prefix);
    if (r.ok) return r;
    for (const key of r.denied) {
      events.emit(null, { event: 'config.startup.denied_default_secret', part: PART, actor: 'anonymous', target: `config:${key}`, reason: '기본값이거나 너무 짧은 비밀값' });
    }
    const err = new Error(`secure-kit: 기본값 비밀값으로는 기동하지 않습니다: ${r.denied.join(', ')}`);
    err.denied = r.denied;
    throw err;
  }

  return { check, assert, isSensitiveKey };
}
