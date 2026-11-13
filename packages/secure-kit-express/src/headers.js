// 부품 5 headers · kit.headers()  (수업 3-5·6-6)
// 모든 응답에 보안 헤더·CSP를 넣고, CORS는 허용 출처만 돌려준다. X-Trace-Id도 여기서 붙인다.
// 설정: headers: { csp: "...", hsts: true }, cors: { 'allowed-origins': ['http://shop.example'], 'allowed-methods': [...], 'allow-credentials': false }
// X-Powered-By는 여기서도 지우지만, 앱에서 app.disable('x-powered-by')도 같이 한다.
import { safeTarget } from './events.js';
import { traceId } from './http.js';

const PART = 'headers';
const DEFAULT_CSP = "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'none'; form-action 'self'";

export function createHeaders({ events, cfg = {}, cors = {}, enabled = true }) {
  const csp = cfg.csp ?? DEFAULT_CSP;
  const origins = new Set(cors['allowed-origins'] ?? []);
  const methods = (cors['allowed-methods'] ?? ['GET', 'POST', 'PUT', 'DELETE']).join(', ');
  const allowHeaders = (cors['allowed-headers'] ?? ['Content-Type', 'Authorization', 'Idempotency-Key', 'X-Trace-Id']).join(', ');
  const credentials = cors['allow-credentials'] === true;

  return function headers(req, res, next) {
    traceId(req, res);
    if (!enabled) return next(); // 영상 시연용 스위치: 추적 번호만 붙인다
    res.removeHeader('X-Powered-By');
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'no-referrer');
    res.setHeader('Content-Security-Policy', csp);
    res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
    if (cfg.hsts !== false) res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');

    const origin = req.headers.origin;
    if (origin === undefined) return next();
    res.setHeader('Vary', 'Origin'); // 출처마다 응답이 다르다는 것을 캐시에 알린다
    if (!origins.has(origin)) {
      // 막지는 않는다(브라우저가 응답을 못 읽을 뿐). 낯선 출처가 왔다는 것만 남긴다
      events.emit(req, { event: 'cors.denied_origin', part: PART, level: 'INFO', target: safeTarget('origin', origin), reason: '허용 목록 밖 출처' });
      if (req.method === 'OPTIONS') {
        res.statusCode = 403;
        return res.end();
      }
      return next();
    }
    res.setHeader('Access-Control-Allow-Origin', origin);
    if (credentials) res.setHeader('Access-Control-Allow-Credentials', 'true');
    if (req.method === 'OPTIONS' && req.headers['access-control-request-method']) {
      res.setHeader('Access-Control-Allow-Methods', methods);
      res.setHeader('Access-Control-Allow-Headers', allowHeaders);
      res.setHeader('Access-Control-Max-Age', '600');
      res.statusCode = 204;
      return res.end();
    }
    next();
  };
}
