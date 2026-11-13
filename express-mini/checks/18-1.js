import { expectStatus } from '../tools/expect.js';

export default [
  {
    name: '로그인하지 않은 사람은 관리자 주문 목록을 볼 수 없다',
    hint: '관리자 라우터가 로그인 검사보다 먼저 연결되지 않았나요?',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/orders'), 401, '로그인하지 않은 요청');
    },
  },
  {
    name: '로그인한 관리자는 주문 목록을 볼 수 있다',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/orders', { token: await h.login('admin') }), 200, '관리자 요청');
    },
  },
];
