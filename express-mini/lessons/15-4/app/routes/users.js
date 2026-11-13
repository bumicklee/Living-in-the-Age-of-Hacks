// 내 정보 보기·고치기.
import { Router } from 'express';

export function usersRouter({ db, auth }) {
  const r = Router();
  // ✏️ 2-4: 고객이 스스로 바꿀 수 있는 칸만 적어 둔다(역할·아이디·비밀번호는 여기서 바꾸지 않는다)
  const EDITABLE = ['name', 'phone', 'address'];

  r.get('/me', auth.requireLogin, (req, res) => {
    res.json(db.prepare('SELECT id, username, name, phone, address, role FROM users WHERE id = ?').get(req.user.id));
  });

  r.patch('/me', auth.requireLogin, (req, res) => {
    const fields = Object.keys(req.body ?? {}).filter((k) => EDITABLE.includes(k));
    if (fields.length === 0) return res.status(400).json({ message: '바꿀 내용이 없습니다' });
    const sets = fields.map((k) => `${k} = ?`).join(', ');
    db.prepare(`UPDATE users SET ${sets} WHERE id = ?`).run(...fields.map((k) => String(req.body[k])), req.user.id);
    res.json({ message: '저장했습니다', changed: fields });
  });

  return r;
}
