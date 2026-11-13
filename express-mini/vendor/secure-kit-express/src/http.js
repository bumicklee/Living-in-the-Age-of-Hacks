// 여러 부품이 같이 쓰는 HTTP 도우미: 추적 번호, 행위자, JSON 오류 응답, 본문 읽기.
import { randomBytes } from 'node:crypto';

const TRACE_OK = /^[A-Za-z0-9._-]{8,64}$/;

/** 요청의 X-Trace-Id가 안전한 모양이면 그 값, 아니면 새로 만든다. 응답 머리에도 넣는다 */
export function traceId(req, res) {
  if (!req.secureKitTraceId) {
    const given = req.headers['x-trace-id'];
    req.secureKitTraceId = typeof given === 'string' && TRACE_OK.test(given) ? given : randomBytes(8).toString('hex');
  }
  if (res && !res.headersSent) res.setHeader('X-Trace-Id', req.secureKitTraceId);
  return req.secureKitTraceId;
}

/**
 * 행위자 규칙(CONTRACT 15번): req.secureKitActor(앱이 직접 넣은 값) → 파트너 → 사람(admin/user) → anonymous
 * 다른 인증을 쓰는 앱은 인증 미들웨어에서 req.secureKitActor = 'admin:아이디' 처럼 넣으면 된다.
 */
export function actorOf(req) {
  if (!req) return 'anonymous';
  if (typeof req.secureKitActor === 'string' && req.secureKitActor) return req.secureKitActor;
  if (req.secureKitPartner?.app) return `partner:${req.secureKitPartner.app}`;
  const u = req.secureKitUser;
  if (u?.sub) return `${u.roles?.includes('admin') ? 'admin' : 'user'}:${u.sub}`;
  return 'anonymous';
}

/** 부품이 던지는 오류. errorResponse()가 { code, message, traceId, ...body } 로 바꿔 보낸다 */
export class KitHttpError extends Error {
  constructor(status, message, { body, headers } = {}) {
    super(message);
    this.name = 'KitHttpError';
    this.status = status;
    this.body = body;
    this.headers = headers;
    this.expose = true;
  }
}

/** 계약의 오류 모양 { code, message, traceId } 로 바로 보낸다 */
export function sendError(req, res, status, message, extra = {}, headers = {}) {
  const tid = traceId(req, res);
  for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
  sendJson(res, status, { code: status, message, traceId: tid, ...extra });
}

export function sendJson(res, status, body) {
  const text = JSON.stringify(body);
  res.statusCode = status;
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  res.setHeader('Content-Length', Buffer.byteLength(text));
  res.end(text);
}

/**
 * 본문 바이트를 상한까지만 읽는다. 상한을 넘으면 나머지는 버리면서 끝까지 받은 뒤 { tooLarge: true }.
 * (중간에 연결을 끊으면 클라이언트가 413 응답을 못 받는 경우가 있어서 끝까지 흘려보낸다. 상한의 8배를 넘으면 끊는다)
 */
export function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    if (req.rawBody) return resolve({ buffer: req.rawBody, tooLarge: req.rawBody.length > limit });
    const chunks = [];
    let size = 0;
    let tooLarge = false;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > limit) {
        tooLarge = true;
        if (size > limit * 8) req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve({ buffer: tooLarge ? null : Buffer.concat(chunks), tooLarge }));
    req.on('error', reject);
  });
}

/** async 미들웨어의 오류를 next(err)로 넘긴다(Express 4는 스스로 잡지 않는다) */
export const wrapAsync = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

/**
 * 응답을 끝내는 순간(res.end) 한 번 부른다. 'finish' 이벤트보다 앞이라
 * 클라이언트가 응답을 받고 바로 다음 요청을 보내도 기록·횟수가 이미 반영돼 있다.
 */
export function onResponseEnd(res, fn) {
  const end = res.end;
  let done = false;
  res.end = function patchedEnd(...args) {
    if (!done) {
      done = true;
      try {
        fn(res.statusCode);
      } catch { /* 기록 실패가 응답을 망치지 않게 */ }
    }
    return end.apply(this, args);
  };
}
