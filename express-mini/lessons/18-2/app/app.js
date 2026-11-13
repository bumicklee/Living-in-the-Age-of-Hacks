// 앱 조립: 요청은 위에서 아래 순서로 지나간다.
import express from 'express';
import { createAuth } from './auth.js';
import { createLogger, requestLogger } from './lib/log.js';
import { errorHandler } from './lib/errors.js';
import { ordersRouter } from './routes/orders.js';
import { usersRouter } from './routes/users.js';
import { productsRouter } from './routes/products.js';
import { reviewsRouter } from './routes/reviews.js';
import { imagesRouter } from './routes/images.js';
import { couponsRouter } from './routes/coupons.js';
import { adminRouter } from './routes/admin.js';

export function createApp({ config, db, labNet }) {
  const app = express();
  const log = createLogger(config.dataDir);
  const auth = createAuth({ config, db });

  app.use(express.json({ limit: '100kb' }));
  app.use(requestLogger(log));
  app.use(auth.authenticate);

  app.get('/api/health', (req, res) => res.json({ ok: true }));
  app.get('/api/partner/report', async (req, res) => {
    try {
      await Promise.reject(new Error('파트너 확인 서비스가 멈췄습니다'));
    } catch {
      // 오류를 숨기고 정상 응답으로 계속 진행하는 잘못된 예.
      return res.json({ approved: true, report: '고객 자료' });
    }
  });
  app.post('/api/login', auth.login);
  app.post('/api/logout', auth.requireLogin, auth.logout);

  app.use(productsRouter({ db }));
  app.use('/api', ordersRouter({ db, auth }));
  app.use('/api', usersRouter({ db, auth }));
  app.use('/api', reviewsRouter({ db, auth }));
  app.use('/api', imagesRouter({ config, auth, labNet }));
  app.use('/api', couponsRouter({ db, auth }));
  app.use('/api/admin', auth.requireLogin, auth.authorize, adminRouter({ db, log }));

  app.get('/', (req, res) => {
    const products = db.prepare('SELECT id, name, price FROM products WHERE hidden = 0 ORDER BY id').all();
    const items = products.map((p) => `<li><a href="/products/${p.id}">${p.name}</a> · ${p.price.toLocaleString('ko-KR')}원</li>`).join('\n');
    res.type('html').send(`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>다온마켓 Express 미니</title></head>
<body><h1>다온마켓 Express 미니</h1><p>보안 실습용 가게입니다. 모든 값은 가짜입니다.</p>
<ul>
${items}
</ul></body></html>`);
  });

  app.use(errorHandler(log));
  return app;
}
