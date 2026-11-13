import { login, request } from '../tools/http.js';

export default async function () {
  const health = await request('GET', '/api/health');
  const me = await request('GET', '/api/me', { token: await login('alice') });
  const header = health.headers.get('x-content-type-options') ?? '없음';
  const cache = me.headers.get('cache-control') ?? '없음';
  const green = header === 'nosniff' && cache.includes('no-store');
  return {
    red: !green,
    lines: [`보안 헤더 X-Content-Type-Options: ${header}`, `로그인한 응답 Cache-Control: ${cache}`],
    verdict: green ? '보안 헤더와 로그인 응답 저장 금지가 연결됐습니다' : '보안 헤더 또는 로그인 응답 저장 금지가 빠졌습니다',
  };
}
