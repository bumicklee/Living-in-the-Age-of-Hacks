// 리뷰 쓰기.
import { Router } from 'express';

export function reviewsRouter({ db, auth }) {
  const r = Router();

  r.post('/reviews', auth.requireLogin, (req, res) => {
    const { productId, body } = req.body ?? {};
    db.prepare('INSERT INTO reviews (product_id, user_id, body) VALUES (?, ?, ?)')
      .run(Number(productId), req.user.id, body ?? null);
    res.status(201).json({ message: '리뷰를 남겼습니다' });
  });

  return r;
}
