// 쿠폰 쓰기.
import { Router } from 'express';
import { payments } from '../lib/payments.js';

export function couponsRouter({ db, auth }) {
  const r = Router();

  // ✏️ 10-2: 남은 수량을 읽고, 결제를 기다린 뒤, 읽어 둔 값으로 다시 쓴다
  r.post('/coupons/redeem', auth.requireLogin, async (req, res) => {
    const code = String(req.body?.code ?? '');
    const coupon = db.prepare('SELECT code, remaining FROM coupons WHERE code = ?').get(code);
    if (!coupon || coupon.remaining <= 0) return res.status(409).json({ message: '쿠폰을 쓸 수 없습니다' });
    await payments.charge(req.user.id, 1000);
    db.prepare('UPDATE coupons SET remaining = ? WHERE code = ?').run(coupon.remaining - 1, code);
    db.prepare('INSERT INTO coupon_uses (code, user_id) VALUES (?, ?)').run(code, req.user.id);
    res.json({ message: '쿠폰을 썼습니다' });
  });

  return r;
}
