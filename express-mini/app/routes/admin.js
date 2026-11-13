// 관리자 화면용 API. app.js에서 로그인·권한 검사 뒤에 연결한다.
import { Router } from 'express';

export function adminRouter({ db, log }) {
  const r = Router();

  r.get('/orders', (req, res) => {
    res.json(db.prepare('SELECT id, user_id, item, amount, status FROM orders ORDER BY id').all());
  });

  r.get('/customers', (req, res) => {
    const rows = db.prepare("SELECT id, name, phone FROM users WHERE role = 'customer' ORDER BY id").all();
    res.json(rows);
  });

  r.get('/customers/:id', (req, res) => {
    const row = db.prepare("SELECT id, name, phone, address FROM users WHERE id = ? AND role = 'customer'").get(Number(req.params.id));
    if (!row) return res.status(404).json({ message: '고객을 찾을 수 없습니다' });
    res.json(row);
  });

  // 새로 만든 고객 내보내기 기능(아직 권한표에 등록하지 않았다)
  r.get('/export', (req, res) => {
    res.json(db.prepare("SELECT id, name, phone, address FROM users WHERE role = 'customer' ORDER BY id").all());
  });

  return r;
}
