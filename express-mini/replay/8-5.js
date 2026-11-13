// alice로 로그인한 뒤, 그 사이 앱 로그(data/app.log)에 새로 쌓인 줄에서 비밀번호가 몇 번 보이는지 센다.
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { login } from '../tools/http.js';
import { DATA } from '../tools/files.js';

export default async function () {
  // 이번 재연에서 새로 쌓인 줄만 센다
  const f = join(DATA, 'app.log');
  const before = existsSync(f) ? readFileSync(f, 'utf8').length : 0;
  await login('alice');
  const text = existsSync(f) ? readFileSync(f, 'utf8').slice(before) : '';
  const hits = text.split('alice-1234').length - 1;
  const lines = ['alice로 로그인했습니다', `앱 로그에서 alice의 비밀번호가 보인 횟수: ${hits}`];
  if (hits > 0) return { red: true, lines, verdict: `로그에 비밀번호가 ${hits}번 남았습니다` };
  return { red: false, lines, verdict: '로그에 비밀번호가 없습니다(요청 기록은 남음)' };
}
