// 앱이 알려진 기본값(keyboard cat)으로 서명하고 있다면, 그 값으로 관리자 토큰을 직접 만들어 본다.
import { createHmac } from 'node:crypto';
import { request } from '../tools/http.js';

export default async function () {
  const body = Buffer.from(JSON.stringify({ sub: 5, role: 'admin', exp: Math.floor(Date.now() / 1000) + 600 })).toString('base64url');
  const forged = `${body}.${createHmac('sha256', 'keyboard cat').update(body).digest('base64url')}`;
  const res = await request('GET', '/api/admin/customers', { token: forged });
  const lines = ['keyboard cat 으로 서명한 관리자 토큰을 직접 만들었습니다', `위조 토큰 → 고객 목록: ${res.status}`];
  if (res.status === 200) return { red: true, lines, verdict: `로그인 없이 만든 토큰으로 고객 ${res.json.length}명을 봤습니다` };
  return { red: false, lines, verdict: '알려진 값으로 만든 토큰은 받지 않습니다' };
}
