// 관리자가 고객 목록을 본 뒤 감사 기록이 몇 줄 늘었는지 센다.
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { login, request } from '../tools/http.js';
import { DATA } from '../tools/files.js';

const count = () => {
  const f = join(DATA, 'audit.log');
  return existsSync(f) ? readFileSync(f, 'utf8').split('\n').filter(Boolean).length : 0;
};

export default async function () {
  const before = count();
  const res = await request('GET', '/api/admin/customers', { token: await login('admin') });
  const added = count() - before;
  const lines = [`관리자 → 고객 목록: ${res.status} (${res.json?.length ?? 0}명)`, `감사 기록(data/audit.log)에 늘어난 줄: ${added}`];
  if (added === 0) return { red: true, lines, verdict: '고객 정보를 봤는데 감사 기록이 0줄입니다' };
  return { red: false, lines, verdict: '누가·무엇을·몇 건 봤는지 감사 기록 1줄이 남았습니다' };
}
