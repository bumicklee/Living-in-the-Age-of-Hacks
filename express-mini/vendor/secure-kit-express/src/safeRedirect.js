// 부품 3 safe-redirect · kit.safeRedirect()  (수업 11-2)
// 로그인 뒤 ?next= 처럼 남이 정한 값으로 이동시킬 때 res.redirect(next) 대신 쓴다(③).
// 받아 주는 것: ① '/'로 시작하는 상대 경로(단 '//'·'/\' 시작은 아님)  ② 허용 호스트의 https 주소
// 나머지는 기본 주소('/')로 보내고 이벤트를 남긴다.
// 설정: 'safe-redirect': { 'allowed-hosts': ['shop.example'], fallback: '/' }
import { safeTarget } from './events.js';

const PART = 'safe-redirect';
const CONTROL = /[\x00-\x1f\x7f\\]/; // 줄바꿈(머리 주입)·역슬래시(브라우저가 /로 바꿔 읽음)

export function createSafeRedirect({ events, cfg = {} }) {
  const hosts = new Set((cfg['allowed-hosts'] ?? []).map((h) => String(h).toLowerCase()));
  const fallback = cfg.fallback ?? '/';

  /** 안전하면 그 값, 아니면 null */
  function check(value) {
    if (typeof value !== 'string' || value.length === 0 || value.length > 2048) return null;
    if (value.startsWith('/')) {
      if (value.startsWith('//') || value.startsWith('/\\') || CONTROL.test(value)) return null;
      return value;
    }
    if (CONTROL.test(value)) return null;
    let url;
    try {
      url = new URL(value);
    } catch {
      return null;
    }
    if (url.protocol !== 'https:' || url.username || url.password) return null;
    return hosts.has(url.hostname.toLowerCase()) ? value : null;
  }

  /**
   * 안전한 이동 주소를 돌려준다(막히면 fallback + 이벤트).
   * @example res.redirect(302, kit.safeRedirect().resolve(req, req.query.next))
   */
  function resolve(req, value) {
    const ok = check(value);
    if (ok !== null) return ok;
    let host = 'invalid';
    try {
      host = new URL(String(value), 'http://relative.invalid').hostname;
    } catch { /* 형식 오류는 invalid 로 남긴다 */ }
    events.emit(req, { event: 'redirect.denied.not_allowed', part: PART, target: safeTarget('host', host), reason: '허용하지 않은 이동 주소' });
    return fallback;
  }

  /** express 없이도 쓰도록 머리를 직접 쓴다 */
  function redirect(req, res, value, status = 302) {
    res.statusCode = status;
    res.setHeader('Location', resolve(req, value));
    res.end();
  }

  return { check, resolve, redirect };
}
