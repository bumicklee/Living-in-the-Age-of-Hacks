// carol 계정에 틀린 비밀번호를 30번 보낸다. 끝나면 앱을 다시 시작해야 carol이 풀린다(정답 적용 시).
import { request } from '../tools/http.js';

export default async function () {
  const counts = {};
  for (let i = 1; i <= 30; i += 1) {
    const res = await request('POST', '/api/login', { body: { username: 'carol', password: `guess-${i}` } });
    counts[res.status] = (counts[res.status] ?? 0) + 1;
  }
  const lines = [`carol에게 틀린 비밀번호 30번: ${Object.entries(counts).map(([s, n]) => `${s} 응답 ${n}번`).join(', ')}`];
  if (!counts[429]) return { red: true, lines, verdict: '30번 모두 비밀번호를 확인했습니다(막히지 않음)' };
  lines.push('carol은 15분 동안 잠겼습니다. 바로 풀려면 앱을 다시 시작하세요');
  return { red: false, lines, verdict: `5번 실패 뒤 ${counts[429]}번은 429로 막혔습니다` };
}
