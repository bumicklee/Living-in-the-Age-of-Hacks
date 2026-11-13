import { BASE_URL } from '../tools/http.js';

const idOf = (cookie) => /session=([^;]+)/.exec(cookie ?? '')?.[1];

export default async function () {
  const login = await fetch(BASE_URL + '/session/login', {
    method: 'POST', headers: { Cookie: 'session=known-before-login' },
  });
  const setCookie = login.headers.get('set-cookie') ?? '';
  const reused = idOf(setCookie) === 'known-before-login';
  const cookie = `session=${idOf(setCookie)}`;
  await fetch(BASE_URL + '/session/logout', { method: 'POST', headers: { Cookie: cookie } });
  const after = await fetch(BASE_URL + '/session/me', { headers: { Cookie: cookie } });
  const red = reused || after.status === 200 || !setCookie.includes('HttpOnly');
  return {
    red,
    lines: [
      `로그인 전 번호 재사용: ${reused ? '예' : '아니요'}`,
      `쿠키에 HttpOnly·Secure·SameSite: ${['HttpOnly', 'Secure', 'SameSite=Lax'].every((x) => setCookie.includes(x)) ? '모두 있음' : '빠진 설정 있음'}`,
      `로그아웃 뒤 같은 번호 요청: ${after.status}`,
    ],
    verdict: red ? '세션 번호나 쿠키 설정, 로그아웃 처리가 부족합니다' : '로그인 때 새 번호를 주고 로그아웃 때 세션을 지웁니다',
  };
}
