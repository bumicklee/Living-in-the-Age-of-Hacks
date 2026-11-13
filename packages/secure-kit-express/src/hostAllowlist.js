// 부품 2 host-allowlist · kit.hostAllowlist()  (수업 2-6)
// 낯선 Host 머리로 들어온 요청(DNS 리바인딩, Host 머리 오염)을 모든 경로에서 400으로 막는다. 포트는 보지 않는다.
// 설정: 'host-allowlist': ['127.0.0.1', 'localhost', 'shop.example']
//   또는 'host-allowlist': { hosts: [...] }  (enabled·fail-open 같은 스위치를 같이 둘 때)
import { safeTarget } from './events.js';
import { sendError } from './http.js';

const PART = 'host-allowlist';

/** 'shop.example:8080' → 'shop.example', '[::1]:80' → '::1' */
export function hostWithoutPort(value) {
  const v = String(value ?? '').trim().toLowerCase();
  if (v.startsWith('[')) return v.slice(1, v.indexOf(']') > 0 ? v.indexOf(']') : undefined);
  const i = v.lastIndexOf(':');
  return i > -1 && v.indexOf(':') === i ? v.slice(0, i) : v;
}

export function allowedHostsOf(raw) {
  const list = Array.isArray(raw) ? raw : raw?.hosts ?? raw?.['allowed-hosts'] ?? [];
  return new Set(list.map((h) => hostWithoutPort(h)));
}

export function createHostAllowlist({ events, raw }) {
  const allowed = allowedHostsOf(raw);
  if (allowed.size === 0) throw new Error('secure-kit: host-allowlist가 비어 있습니다(모든 Host를 막게 됩니다)');

  return function hostAllowlist(req, res, next) {
    const given = req.headers.host ?? '';
    if (allowed.has(hostWithoutPort(given))) return next();
    events.emit(req, { event: 'request.denied.host', part: PART, target: safeTarget('host', given), reason: '허용 목록 밖 Host' });
    sendError(req, res, 400, '잘못된 요청입니다');
  };
}
