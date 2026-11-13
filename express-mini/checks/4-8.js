import { expectStatus } from '../tools/expect.js';

export default [
  {
    name: '로그인한 토큰으로 내 정보를 본다',
    async run(h) {
      expectStatus(await h.request('GET', '/api/me', { token: await h.login('alice') }), 200, '내 정보');
    },
  },
  {
    name: '로그아웃한 토큰은 401',
    hint: '로그아웃할 때 토큰 번호(jti)를 폐기 목록에 넣고, 토큰을 읽을 때 확인하나요?',
    async run(h) {
      const token = await h.login('alice');
      expectStatus(await h.request('POST', '/api/logout', { token }), 204, '로그아웃');
      expectStatus(await h.request('GET', '/api/me', { token }), 401, '로그아웃한 토큰');
    },
  },
  {
    name: '16분이 지난 토큰은 401',
    hint: '토큰에 만료 시각(exp, 15분)을 넣고, 읽을 때 지났는지 확인하나요?',
    async run(h) {
      const token = await h.login('alice');
      const start = Date.now();
      h.clock.now = () => start + 16 * 60 * 1000;
      expectStatus(await h.request('GET', '/api/me', { token }), 401, '16분 뒤');
    },
  },
  {
    name: '14분이 지난 토큰은 아직 쓴다',
    async run(h) {
      const token = await h.login('alice');
      const start = Date.now();
      h.clock.now = () => start + 14 * 60 * 1000;
      expectStatus(await h.request('GET', '/api/me', { token }), 200, '14분 뒤');
    },
  },
];
