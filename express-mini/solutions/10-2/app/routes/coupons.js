// 쿠폰 쓰기.
import { Router } from 'express';
import { payments } from '../lib/payments.js';

export function couponsRouter({ db, auth }) {
  const r = Router();

  // ✏️ 10-2: 「남아 있으면 하나 줄이기」를 DB 문장 하나로 한다. 줄인 행이 없으면 다른 요청이 먼저 쓴 것이다.
  r.post('/coupons/redeem', auth.requireLogin, async (req, res) => {
    const code = String(req.body?.code ?? '');
    const taken = db.prepare('UPDATE coupons SET remaining = remaining - 1 WHERE code = ? AND remaining > 0').run(code);
    if (taken.changes !== 1) return res.status(409).json({ message: '쿠폰을 쓸 수 없습니다' });
    await payments.charge(req.user.id, 1000);
    db.prepare('INSERT INTO coupon_uses (code, user_id) VALUES (?, ?)').run(code, req.user.id);
    res.json({ message: '쿠폰을 썼습니다' });
  });

  return r;
}
