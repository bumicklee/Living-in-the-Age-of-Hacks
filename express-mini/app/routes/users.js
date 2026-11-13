// 내 정보 보기·고치기.
import { Router } from 'express';

export function usersRouter({ db, auth }) {
  const r = Router();
  // users 표의 칸 이름 목록
  const USER_COLUMNS = db.prepare('PRAGMA table_info(users)').all().map((c) => c.name);

  r.get('/me', auth.requireLogin, (req, res) => {
    res.json(db.prepare('SELECT id, username, name, phone, address, role FROM users WHERE id = ?').get(req.user.id));
  });

  // ✏️ 2-4: 요청 본문에 있는 칸을 표에 있기만 하면 모두 저장한다
  r.patch('/me', auth.requireLogin, (req, res) => {
    const fields = Object.keys(req.body ?? {}).filter((k) => USER_COLUMNS.includes(k) && k !== 'id');
    if (fields.length === 0) return res.status(400).json({ message: '바꿀 내용이 없습니다' });
    const sets = fields.map((k) => `${k} = ?`).join(', ');
    db.prepare(`UPDATE users SET ${sets} WHERE id = ?`).run(...fields.map((k) => String(req.body[k])), req.user.id);
    res.json({ message: '저장했습니다', changed: fields });
  });

  return r;
}
