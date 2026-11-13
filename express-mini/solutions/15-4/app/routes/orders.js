// 고객 주문 조회.
import { Router } from 'express';

export function ordersRouter({ db, auth }) {
  const r = Router();

  r.get('/orders', auth.requireLogin, (req, res) => {
    res.json(db.prepare('SELECT id, item, amount, status FROM orders WHERE user_id = ?').all(req.user.id));
  });

  // ✏️ 2-2: 주문 번호와 로그인한 사람을 함께 조건에 넣는다(남의 주문은 없는 주문과 똑같이 404)
  r.get('/orders/:id', auth.requireLogin, (req, res) => {
    const order = db.prepare('SELECT id, item, amount, address, status FROM orders WHERE id = ? AND user_id = ?')
      .get(Number(req.params.id), req.user.id);
    if (!order) return res.status(404).json({ message: '주문을 찾을 수 없습니다' });
    res.json(order);
  });

  return r;
}
