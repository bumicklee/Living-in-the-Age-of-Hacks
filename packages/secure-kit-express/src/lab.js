// 랩 전용 엔드포인트 /_kit/* · kit.lab()   ← 회사 서비스에서는 끈다(secure-kit.lab.expose: false, 기본값)
// 계약 시험이 상태를 지우고(reset) 이벤트·감사 기록·로그를 읽는 통로다. 라우터 없이 경로를 직접 본다.
// 앱마다 다른 것(예: /_kit/pii/raw/:id)은 kit.lab({ routes: { 'GET /_kit/pii/raw/:id': handler } }) 로 더한다.
import { readBody, sendError, sendJson } from './http.js';

/** 'GET /_kit/approvals/:id/approve' → { method, re, names } */
function compile(spec) {
  const [method, path] = spec.split(' ');
  const names = [];
  const re = new RegExp(`^${path.replace(/:([a-zA-Z]+)/g, (_, n) => { names.push(n); return '([^/]+)'; })}$`);
  return { method, re, names };
}

export function createLab({ kit, expose, routes = {} }) {
  const builtins = {
    'POST /_kit/reset': async (req, res) => {
      await kit.reset();
      res.statusCode = 204;
      res.end();
    },
    'GET /_kit/events': (req, res, { query }) => sendJson(res, 200, kit.events.after(Number(query.get('after') ?? 0) || 0)),
    'GET /_kit/audit': (req, res) => sendJson(res, 200, { records: kit.auditTrail.recent() }),
    'GET /_kit/logs': (req, res) => sendJson(res, 200, { lines: kit.logger().lines() }),
    'GET /_kit/outbox': (req, res) => sendJson(res, 200, { mails: kit.outbox.slice(-50) }),
    'POST /_kit/startup/check': (req, res, { body }) => sendJson(res, 200, kit.startupCheck().check(body ?? {})),
    'POST /_kit/partner/token': (req, res, { body }) => {
      const app = typeof body?.app === 'string' ? body.app : '';
      if (!/^[a-z0-9-]{1,32}$/.test(app)) return sendError(req, res, 400, '잘못된 요청입니다');
      const scopes = Array.isArray(body.scopes) ? body.scopes.filter((s) => typeof s === 'string') : [];
      sendJson(res, 200, { token: kit.token().issuePartner(app, scopes).token });
    },
    'POST /_kit/approvals/:id/approve': async (req, res, { params, body }) => {
      sendJson(res, 200, await kit.approvals.approve(params.id, typeof body?.approver === 'string' ? body.approver.slice(0, 64) : '', req));
    }
  };
  const table = Object.entries({ ...builtins, ...routes }).map(([spec, fn]) => ({ ...compile(spec), fn }));

  return async function lab(req, res, next) {
    const url = new URL(req.originalUrl ?? req.url, 'http://lab.invalid');
    if (!url.pathname.startsWith('/_kit/') || !expose) return next();
    for (const r of table) {
      const m = r.method === req.method && r.re.exec(url.pathname);
      if (!m) continue;
      try {
        let body = req.body;
        if (body === undefined && req.method === 'POST') {
          const { buffer } = await readBody(req, 64 * 1024);
          body = buffer && buffer.length ? JSON.parse(buffer.toString('utf8')) : {};
        }
        const params = Object.fromEntries(r.names.map((n, i) => [n, decodeURIComponent(m[i + 1])]));
        return await r.fn(req, res, { params, query: url.searchParams, body });
      } catch (err) {
        return next(err);
      }
    }
    next();
  };
}
