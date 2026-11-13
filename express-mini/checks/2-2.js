import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: 'alice는 자기 주문 1001을 본다',
    async run(h) {
      const res = await h.request('GET', '/api/orders/1001', { token: await h.login('alice') });
      expectStatus(res, 200, 'alice의 주문 1001');
    },
  },
  {
    name: 'bob은 alice의 주문 1001을 볼 수 없다(없는 주문과 같은 404)',
    hint: '주문을 찾는 SQL에 로그인한 사람의 번호(req.user.id)도 조건으로 넣었나요?',
    async run(h) {
      const res = await h.request('GET', '/api/orders/1001', { token: await h.login('bob') });
      expectStatus(res, 404, 'bob이 alice의 주문 1001을 요청');
      expectTrue(!res.text.includes('연습로 1'), 'alice의 주소가 응답에 보입니다');
    },
  },
  {
    name: 'bob은 자기 주문 2001을 본다',
    async run(h) {
      const res = await h.request('GET', '/api/orders/2001', { token: await h.login('bob') });
      expectStatus(res, 200, 'bob의 주문 2001');
    },
  },
  {
    name: '로그인하지 않으면 401',
    async run(h) {
      expectStatus(await h.request('GET', '/api/orders/1001'), 401, '로그인 없이 주문 요청');
    },
  },
];
