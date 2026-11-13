// 부품 1 safe-http-client · kit.safeFetch()  (수업 2-5)
// 서버가 남 대신 URL을 부를 때 쓴다. 일반 fetch 대신 이것으로 바꿔 쓴다(③).
// 1) http·https만  2) 이름을 풀어 나온 「모든」 주소가 내부망이 아닌지  3) 확인한 그 주소로만 붙는다(DNS 리바인딩)
// 4) 리다이렉트는 따라가지 않는다(따라가면 1~3을 우회한다)  5) 시간·크기 상한
import { lookup } from 'node:dns/promises';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import { safeTarget } from './events.js';
import { KitHttpError } from './http.js';

const PART = 'safe-http-client';

/** 내부·특수 주소인가(IPv4·IPv6, IPv4가 섞인 IPv6 포함) */
export function isInternalAddress(ip) {
  if (net.isIPv4(ip)) {
    const [a, b] = ip.split('.').map(Number);
    return (
      a === 0 || a === 10 || a === 127 || a >= 224 ||
      (a === 100 && b >= 64 && b <= 127) || // 통신사 NAT
      (a === 169 && b === 254) || // 링크 로컬·클라우드 메타데이터
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 192 && b === 0) ||
      (a === 198 && (b === 18 || b === 19))
    );
  }
  if (net.isIPv6(ip)) {
    const v = ip.toLowerCase();
    const mapped = /^(?:0*:)*:?ffff:(\d+\.\d+\.\d+\.\d+)$/.exec(v) ?? /^::(\d+\.\d+\.\d+\.\d+)$/.exec(v);
    if (mapped) return isInternalAddress(mapped[1]);
    if (/^::ffff:[0-9a-f]{1,4}:[0-9a-f]{1,4}$/.test(v)) return true; // 16진수로 쓴 IPv4 매핑: 그냥 막는다
    return v === '::' || v === '::1' || /^f[cd]/.test(v) || /^fe[89ab]/.test(v) || /^ff/.test(v) || v.startsWith('64:ff9b:');
  }
  return true; // 모양을 모르면 막는다
}

export function createSafeFetch({ events, cfg = {} }) {
  const timeoutMs = cfg['timeout-ms'] ?? 5000;
  const maxBytes = cfg['max-bytes'] ?? 1024 * 1024;

  function deny(req, status, event, target, reason) {
    events.emit(req, { event, part: PART, target, reason });
    return new KitHttpError(status, status === 403 ? '허용하지 않는 주소입니다' : '주소 형식이 올바르지 않습니다');
  }

  /**
   * @returns {Promise<{status:number, headers:object, body:Buffer}>}  막히면 KitHttpError(400·403)를 던진다
   */
  return async function safeFetch(rawUrl, { method = 'GET', headers = {}, req } = {}) {
    let url;
    try {
      url = new URL(String(rawUrl));
    } catch {
      throw deny(req, 400, 'egress.denied.bad_url', 'url:invalid', 'URL 형식 오류');
    }
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      throw deny(req, 400, 'egress.denied.scheme', safeTarget('scheme', url.protocol.replace(':', '')), 'http·https 외 스킴');
    }
    const host = url.hostname.replace(/^\[|\]$/g, '');
    if (!host) throw deny(req, 400, 'egress.denied.bad_url', 'url:invalid', '호스트 없음');

    let addresses;
    try {
      addresses = net.isIP(host) ? [{ address: host, family: net.isIP(host) }] : await lookup(host, { all: true, verbatim: true });
    } catch {
      throw deny(req, 400, 'egress.denied.bad_url', safeTarget('host', host), '이름을 풀 수 없음');
    }
    if (addresses.length === 0 || addresses.some((a) => isInternalAddress(a.address))) {
      throw deny(req, 403, 'egress.denied.internal_address', safeTarget('host', host), '내부망·특수 주소로 풀림');
    }
    const pinned = addresses[0];

    return new Promise((resolve, reject) => {
      const lib = url.protocol === 'https:' ? https : http;
      const r = lib.request(url, {
        method,
        headers,
        timeout: timeoutMs,
        // 확인한 주소로만 붙는다: 여기서 이름을 다시 풀지 않는다
        lookup: (_h, opts, cb) => (opts?.all ? cb(null, [pinned]) : cb(null, pinned.address, pinned.family))
      }, (res) => {
        const chunks = [];
        let size = 0;
        res.on('data', (c) => {
          size += c.length;
          if (size > maxBytes) r.destroy(new KitHttpError(502, '응답이 너무 큽니다'));
          else chunks.push(c);
        });
        res.on('end', () => resolve({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
      });
      r.on('timeout', () => r.destroy(new KitHttpError(504, '상대 서버가 응답하지 않습니다')));
      r.on('error', (e) => reject(e instanceof KitHttpError ? e : new KitHttpError(502, '상대 서버에 닿지 않습니다')));
      r.end();
    });
  };
}
