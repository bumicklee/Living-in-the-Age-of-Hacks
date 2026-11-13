import { randomUUID } from 'node:crypto';
import { Router } from 'express';

const sessionId = (req) => /(?:^|;\s*)session=([^;]+)/.exec(req.headers.cookie ?? '')?.[1];

export function sessionDemo() {
  const router = Router();
  const sessions = new Map();

  router.post('/login', (req, res) => {
    // 브라우저가 보낸 번호를 그대로 다시 쓰면 로그인 전 번호가 로그인 뒤에도 살아 있다.
    const id = sessionId(req) ?? randomUUID();
    sessions.set(id, { name: 'alice' });
    res.setHeader('Set-Cookie', `session=${id}; Path=/`);
    res.json({ message: '로그인했습니다' });
  });

  router.get('/me', (req, res) => {
    const user = sessions.get(sessionId(req));
    if (!user) return res.status(401).json({ message: '로그인이 필요합니다' });
    res.json(user);
  });

  router.post('/logout', (req, res) => {
    // 화면에서만 로그아웃하고 서버의 세션은 남긴다.
    res.status(204).end();
  });

  return router;
}
