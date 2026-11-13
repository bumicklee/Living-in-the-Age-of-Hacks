// 로그인·토큰·권한 검사.
import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { verifyPassword } from './lib/password.js';
import { clock } from './lib/clock.js';

const LOGIN_FAILED = '아이디 또는 비밀번호가 올바르지 않습니다';
// ✏️ 4-3: 같은 아이디·같은 IP에서 15분에 5번까지만 틀릴 수 있다
const MAX_FAILURES = 5;
const WINDOW_MS = 15 * 60 * 1000;
const ACCESS_SECONDS = 15 * 60;
const nowSeconds = () => Math.floor(clock.now() / 1000);

// ✏️ 2-3: 관리자 주소마다 들어올 수 있는 역할(권한표)
const PERMISSIONS = [
  { method: 'GET', path: '/api/admin/orders', roles: ['staff', 'admin'] },
  { method: 'GET', path: '/api/admin/customers', roles: ['admin'] },
];

function findRule(req) {
  const path = req.originalUrl.split('?')[0];
  return PERMISSIONS.find((r) => r.method === req.method && (path === r.path || path.startsWith(r.path + '/')));
}

export function createAuth({ config, db }) {
  const failures = new Map(); // '아이디|IP' → 최근 실패 시각 목록
  const revoked = new Set();
  const sign = (body) => createHmac('sha256', config.secret).update(body).digest('base64url');

  function issueToken(user) {
    const claims = { sub: user.id, role: user.role, exp: nowSeconds() + ACCESS_SECONDS, jti: randomUUID() };
    const body = Buffer.from(JSON.stringify(claims)).toString('base64url');
    return `${body}.${sign(body)}`;
  }

  function readToken(token) {
    const [body, sig] = String(token).split('.');
    if (!body || !sig) return null;
    const want = Buffer.from(sign(body));
    const got = Buffer.from(sig);
    if (want.length !== got.length || !timingSafeEqual(want, got)) return null;
    let claims;
    try { claims = JSON.parse(Buffer.from(body, 'base64url').toString()); } catch { return null; }
    if (typeof claims.exp !== 'number' || claims.exp <= nowSeconds() || revoked.has(claims.jti)) return null;
    return claims;
  }

  return {
    issueToken,
    readToken,

    // 모든 요청: Bearer 토큰이 맞으면 req.user 를 채운다
    authenticate(req, res, next) {
      const header = req.headers.authorization ?? '';
      const claims = header.startsWith('Bearer ') ? readToken(header.slice(7)) : null;
      if (claims) req.user = { id: claims.sub, role: claims.role, claims };
      next();
    },

    requireLogin(req, res, next) {
      if (!req.user) return res.status(401).json({ message: '로그인이 필요합니다' });
      next();
    },

    authorize(req, res, next) {
      const rule = findRule(req);
      if (!rule) return res.status(403).json({ message: '권한이 없습니다' });
      if (!rule.roles.includes(req.user.role)) return res.status(403).json({ message: '권한이 없습니다' });
      next();
    },

    login(req, res) {
      const { username, password } = req.body ?? {};
      const key = `${String(username ?? '')}|${req.ip}`;
      const recent = (failures.get(key) ?? []).filter((t) => clock.now() - t < WINDOW_MS);
      if (recent.length >= MAX_FAILURES) {
        return res.status(429).json({ message: '로그인 시도가 너무 많습니다. 잠시 뒤 다시 시도하세요' });
      }
      const user = db.prepare('SELECT * FROM users WHERE username = ?').get(String(username ?? ''));
      if (!user || !verifyPassword(String(password ?? ''), user.password_hash)) {
        failures.set(key, [...recent, clock.now()]);
        return res.status(401).json({ message: LOGIN_FAILED });
      }
      failures.delete(key);
      res.json({ token: issueToken(user), name: user.name, role: user.role });
    },

    logout(req, res) {
      revoked.add(req.user.claims.jti);
      res.status(204).end();
    },
  };
}
