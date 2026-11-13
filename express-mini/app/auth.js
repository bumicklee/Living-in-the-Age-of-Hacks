// 로그인·토큰·권한 검사.
import { createHmac, timingSafeEqual } from 'node:crypto';
import { verifyPassword } from './lib/password.js';

const LOGIN_FAILED = '아이디 또는 비밀번호가 올바르지 않습니다';

// ✏️ 2-3: 관리자 주소마다 들어올 수 있는 역할(권한표)
const PERMISSIONS = [
  { method: 'GET', path: '/api/admin/orders', roles: ['staff', 'admin'] },
];

function findRule(req) {
  const path = req.originalUrl.split('?')[0];
  return PERMISSIONS.find((r) => r.method === req.method && (path === r.path || path.startsWith(r.path + '/')));
}

export function createAuth({ config, db }) {
  const sign = (body) => createHmac('sha256', config.secret).update(body).digest('base64url');

  // ✏️ 4-8: 토큰에 만료 시각이 없다
  function issueToken(user) {
    const body = Buffer.from(JSON.stringify({ sub: user.id, role: user.role })).toString('base64url');
    return `${body}.${sign(body)}`;
  }

  function readToken(token) {
    const [body, sig] = String(token).split('.');
    if (!body || !sig) return null;
    const want = Buffer.from(sign(body));
    const got = Buffer.from(sig);
    if (want.length !== got.length || !timingSafeEqual(want, got)) return null;
    return JSON.parse(Buffer.from(body, 'base64url').toString());
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

    // ✏️ 2-3: 권한표에 없는 주소는 로그인만 하면 통과한다
    authorize(req, res, next) {
      const rule = findRule(req);
      if (!rule) return next();
      if (!rule.roles.includes(req.user.role)) return res.status(403).json({ message: '권한이 없습니다' });
      next();
    },

    // ✏️ 4-3: 로그인 실패 횟수를 세지 않는다
    login(req, res) {
      const { username, password } = req.body ?? {};
      const user = db.prepare('SELECT * FROM users WHERE username = ?').get(String(username ?? ''));
      if (!user || !verifyPassword(String(password ?? ''), user.password_hash)) {
        return res.status(401).json({ message: LOGIN_FAILED });
      }
      res.json({ token: issueToken(user), name: user.name, role: user.role });
    },

    // ✏️ 4-8: 로그아웃해도 토큰을 끊지 않는다
    logout(req, res) {
      res.status(204).end();
    },
  };
}
