// bob이 내 정보 수정 요청에 role: admin 을 끼워 넣는다. 끝나면 bob의 역할을 되돌린다.
import { login, request } from '../tools/http.js';
import { withDb } from '../tools/dbtool.js';

export default async function () {
  const token = await login('bob');
  const res = await request('PATCH', '/api/me', { token, body: { name: '밥', role: 'admin' } });
  const role = withDb((db) => db.prepare('SELECT role FROM users WHERE username = ?').get('bob').role);
  withDb((db) => db.prepare("UPDATE users SET role = 'customer', name = '밥' WHERE username = 'bob'").run());
  const lines = [`bob → 내 정보 수정 {name, role: admin}: ${res.status} ${JSON.stringify(res.json?.changed ?? [])}`, `DB에 저장된 bob의 역할: ${role}`];
  if (role === 'admin') return { red: true, lines, verdict: '고객 bob이 스스로 관리자가 됐습니다' };
  return { red: false, lines, verdict: '이름만 바뀌고 역할은 customer 그대로입니다' };
}
