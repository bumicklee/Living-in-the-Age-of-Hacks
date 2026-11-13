// alice가 로그아웃한 뒤 같은 토큰으로 내 정보를 요청한다.
import { login, request } from '../tools/http.js';

export default async function () {
  const token = await login('alice');
  const claims = JSON.parse(Buffer.from(token.split('.')[0], 'base64url').toString());
  const out = await request('POST', '/api/logout', { token });
  const after = await request('GET', '/api/me', { token });
  const lines = [
    `토큰에 든 만료 시각(exp): ${claims.exp ? new Date(claims.exp * 1000).toLocaleTimeString('ko-KR') : '없음'}`,
    `로그아웃: ${out.status}`,
    `로그아웃한 토큰으로 내 정보: ${after.status}`,
  ];
  if (after.status === 200 || !claims.exp) return { red: true, lines, verdict: '로그아웃한 토큰이 계속 쓰이거나 만료 시각이 없습니다' };
  return { red: false, lines, verdict: '토큰은 15분 뒤 만료되고, 로그아웃한 토큰은 401입니다' };
}
