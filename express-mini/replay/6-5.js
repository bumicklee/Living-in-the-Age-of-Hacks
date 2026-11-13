// 본문 없는 리뷰를 보내 DB 오류를 일으킨다.
import { login, request } from '../tools/http.js';

export default async function () {
  const res = await request('POST', '/api/reviews', { token: await login('bob'), body: { productId: 1 } });
  const leaks = ['reviews.body', 'stack', '.js:'].filter((w) => res.text.includes(w));
  const lines = [`본문 없는 리뷰: ${res.status}`, `응답: ${res.text.slice(0, 120)}${res.text.length > 120 ? '…' : ''}`];
  if (leaks.length > 0) return { red: true, lines, verdict: `응답에 DB 표·칸 이름과 서버 파일 경로가 보입니다(${leaks.join(', ')})` };
  return { red: false, lines, verdict: `응답에는 일반 문구와 추적 번호(${res.json?.traceId})만 있습니다` };
}
