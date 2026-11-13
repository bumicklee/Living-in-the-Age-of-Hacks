// 가게 앱 연결(15-4 완료 지점). 의존성 한 줄 + 설정 + 미들웨어 세 줄(+ 시험용 lab).
import express from 'express';
import { secureKit } from '@secure-kit/express';
import { createShop } from './shop.js';

export function createApp() {
  // 키는 Spring 가게 예제 application.yml 의 secure-kit.* 와 같다
  const kit = secureKit({
    'secure-kit': {
      'error-response': { enabled: true },
      'rate-limit': { login: { 'per-account': '5/10m', 'per-ip': '50/10m' } },
      audit: { sink: 'memory' },
      // 계약 시험용 /_kit/*. 회사 서비스에서는 KIT_LAB_EXPOSE=false(또는 이 줄을 지운다)
      lab: { expose: process.env.KIT_LAB_EXPOSE !== 'false' }
    }
  });
  const shop = createShop();
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '1mb' }));
  app.use(shop.authenticate); // req.secureKitActor 를 넣는다
  app.use(kit.lab());

  app.get('/api/health', (req, res) => res.json({ ok: true }));
  app.post('/api/login', kit.rateLimit('login'), shop.login);
  app.get('/api/orders/:id', shop.getOrder);
  app.get('/api/admin/members/:id', kit.audited('member.read'), shop.requireAdmin, shop.getMember);
  app.post('/api/_reset', shop.reset);

  // 오류 처리: 밖으로는 일반 문구와 추적 번호만, 자세한 내용은 로그에만
  app.use(kit.errorResponse());
  return app;
}
