import { randomUUID } from 'node:crypto';
import { Router } from 'express';

const sessionId = (req) => /(?:^|;\s*)session=([^;]+)/.exec(req.headers.cookie ?? '')?.[1];
const cookieOptions = 'Path=/; HttpOnly; Secure; SameSite=Lax';

export function sessionDemo() {
  const router = Router();
  const sessions = new Map();

  router.post('/login', (req, res) => {
    const oldId = sessionId(req);
    if (oldId) sessions.delete(oldId);
    // 로그인 뒤 새 번호를 준다. 로그인 전에 알던 번호를 계속 쓸 수 없게 한다.
    const id = randomUUID();
    sessions.set(id, { name: 'alice' });
    res.setHeader('Set-Cookie', `session=${id}; ${cookieOptions}`);
    res.json({ message: '로그인했습니다' });
  });

  router.get('/me', (req, res) => {
    const user = sessions.get(sessionId(req));
    if (!user) return res.status(401).json({ message: '로그인이 필요합니다' });
    res.json(user);
  });

  router.post('/logout', (req, res) => {
    const id = sessionId(req);
    if (id) sessions.delete(id);
    res.setHeader('Set-Cookie', `session=; ${cookieOptions}; Max-Age=0`);
    res.status(204).end();
  });

  return router;
}
