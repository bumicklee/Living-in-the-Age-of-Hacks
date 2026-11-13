import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: '수량 1개 쿠폰에 동시 10요청: 성공 1건, 남은 수량 0, 사용 기록 1건',
    hint: '「남아 있으면 하나 줄이기」를 UPDATE … WHERE remaining > 0 한 문장으로 하고, 줄인 행이 1인지 확인하나요?',
    async run(h) {
      const token = await h.login('alice');
      const results = await Promise.all(Array.from({ length: 10 }, () =>
        h.request('POST', '/api/coupons/redeem', { token, body: { code: 'WELCOME' } })));
      const ok = results.filter((r) => r.status === 200).length;
      const left = h.db.prepare("SELECT remaining FROM coupons WHERE code = 'WELCOME'").get().remaining;
      const uses = h.db.prepare("SELECT count(*) AS n FROM coupon_uses WHERE code = 'WELCOME'").get().n;
      expectTrue(ok === 1 && left === 0 && uses === 1, `성공 ${ok}건, 남은 수량 ${left}, 사용 기록 ${uses}건`);
    },
  },
  {
    name: '한 번에 하나씩 쓰면 첫 번째만 성공',
    async run(h) {
      const token = await h.login('bob');
      expectStatus(await h.request('POST', '/api/coupons/redeem', { token, body: { code: 'WELCOME' } }), 200, '첫 사용');
      expectStatus(await h.request('POST', '/api/coupons/redeem', { token, body: { code: 'WELCOME' } }), 409, '두 번째 사용');
    },
  },
  {
    name: '없는 쿠폰은 409',
    async run(h) {
      expectStatus(await h.request('POST', '/api/coupons/redeem', { token: await h.login('bob'), body: { code: 'NOPE' } }), 409, '없는 쿠폰');
    },
  },
];
