// 가게 앱 연결(15-4 시작 지점). 이 파일과 package.json 만 15-4에서 바뀐다.
import express from 'express';
import { createShop } from './shop.js';

export function createApp() {
  const shop = createShop();
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '1mb' }));
  app.use(shop.authenticate);

  app.get('/api/health', (req, res) => res.json({ ok: true }));
  app.post('/api/login', shop.login);
  app.get('/api/orders/:id', shop.getOrder);
  app.get('/api/admin/members/:id', shop.requireAdmin, shop.getMember);
  app.post('/api/_reset', shop.reset);

  // 오류 처리: 예외 내용을 그대로 돌려준다(디버깅하기 편하지만 내부 정보가 밖으로 나간다)
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    res.status(500).json({ message: err.message, stack: err.stack });
  });
  return app;
}
