// 부품 17 limits(본문 상한) · kit.bodyLimit()  (수업 9-3)
// 본문을 상한까지만 읽는다. 넘으면 413 + input.denied.too_large. 읽은 바이트는 req.rawBody(웹훅 서명 확인에 쓴다),
// JSON이면 req.body 로 풀어 둔다(express.json()을 따로 쓰지 않아도 된다. 같이 쓰면 express.json은 건너뛴다).
// 설정: limits: { 'body-max': '1mb', routes: { '/kit/upload': '3mb' } }   ← 경로는 정확히 같은 것만
import { safeTarget } from './events.js';
import { readBody, sendError } from './http.js';
import { parseSize } from './config.js';

const PART = 'limits';
const NO_BODY = new Set(['GET', 'HEAD', 'OPTIONS', 'DELETE']);

export function createBodyLimit({ events, cfg = {}, enabled = true }) {
  const defaultMax = parseSize(cfg['body-max'], 1024 * 1024);
  const routes = Object.fromEntries(Object.entries(cfg.routes ?? {}).map(([p, v]) => [p, parseSize(v)]));

  return async function bodyLimit(req, res, next) {
    if (!enabled || req.rawBody) return next();
    const hasBody = req.headers['content-length'] !== undefined || req.headers['transfer-encoding'] !== undefined;
    if (NO_BODY.has(req.method) && !hasBody) return next();
    const path = (req.originalUrl ?? req.url).split('?')[0];
    const max = routes[path] ?? defaultMax;
    try {
      const { buffer, tooLarge } = await readBody(req, max);
      if (tooLarge) {
        events.emit(req, { event: 'input.denied.too_large', part: PART, target: safeTarget('path', path), reason: `본문 상한 ${max}바이트 초과` });
        return sendError(req, res, 413, '요청이 너무 큽니다');
      }
      req.rawBody = buffer;
      req._body = true; // express.json()·body-parser가 다시 읽지 않게 하는 표시
      const type = String(req.headers['content-type'] ?? '');
      if (/\bjson\b/i.test(type) && buffer.length > 0) {
        try {
          req.body = JSON.parse(buffer.toString('utf8'));
        } catch {
          events.emit(req, { event: 'input.denied.bad_json', part: PART, level: 'INFO', target: safeTarget('path', path), reason: 'JSON 형식 오류' });
          return sendError(req, res, 400, '잘못된 요청입니다');
        }
      } else if (req.body === undefined) {
        req.body = {};
      }
      next();
    } catch (err) {
      next(err);
    }
  };
}
