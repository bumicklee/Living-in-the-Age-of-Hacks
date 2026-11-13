import { expectTrue } from '../tools/expect.js';

const idOf = (setCookie) => /(?:^|\s)session=([^;]+)/.exec(setCookie ?? '')?.[1];
const post = (h, path, cookie) => fetch(h.base + path, {
  method: 'POST', headers: cookie ? { Cookie: cookie } : {},
});
const get = (h, path, cookie) => fetch(h.base + path, {
  headers: { Cookie: cookie },
});

export default [
  {
    name: '로그인하면 새 세션 번호와 쿠키 보호 설정을 받는다',
    hint: '로그인 전 번호를 재사용하지 않고 HttpOnly·Secure·SameSite를 붙였나요?',
    async run(h) {
      const res = await post(h, '/session/login', 'session=known-before-login');
      const cookie = res.headers.get('set-cookie') ?? '';
      expectTrue(res.status === 200, `로그인 응답 ${res.status}`);
      expectTrue(idOf(cookie) !== 'known-before-login', '로그인 전 세션 번호를 다시 썼습니다');
      for (const setting of ['HttpOnly', 'Secure', 'SameSite=Lax']) {
        expectTrue(cookie.includes(setting), `쿠키에 ${setting} 설정이 없습니다`);
      }
    },
  },
  {
    name: '로그아웃하면 서버 세션이 없어지고 쿠키도 지운다',
    hint: '서버의 세션을 삭제하고 Max-Age=0 쿠키를 돌려주나요?',
    async run(h) {
      const login = await post(h, '/session/login');
      const cookie = `session=${idOf(login.headers.get('set-cookie'))}`;
      expectTrue((await get(h, '/session/me', cookie)).status === 200, '로그인 뒤 내 정보가 보이지 않습니다');
      const logout = await post(h, '/session/logout', cookie);
      expectTrue(logout.status === 204, `로그아웃 응답 ${logout.status}`);
      expectTrue((logout.headers.get('set-cookie') ?? '').includes('Max-Age=0'), '쿠키를 지우지 않았습니다');
      expectTrue((await get(h, '/session/me', cookie)).status === 401, '로그아웃한 번호가 아직 쓰입니다');
    },
  },
];
