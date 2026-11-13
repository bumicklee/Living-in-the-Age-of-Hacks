// 수량 1개 쿠폰 WELCOME에 동시 요청 10개를 보낸다. 시작 전에 쿠폰을 1개로 되돌린다.
import { login, request } from '../tools/http.js';
import { withDb } from '../tools/dbtool.js';

export default async function () {
  withDb((db) => db.exec("UPDATE coupons SET remaining = 1 WHERE code = 'WELCOME'; DELETE FROM coupon_uses;"));
  const token = await login('alice');
  const results = await Promise.all(Array.from({ length: 10 }, () => request('POST', '/api/coupons/redeem', { token, body: { code: 'WELCOME' } })));
  const ok = results.filter((r) => r.status === 200).length;
  const { remaining, uses } = withDb((db) => ({
    remaining: db.prepare("SELECT remaining FROM coupons WHERE code = 'WELCOME'").get().remaining,
    uses: db.prepare("SELECT count(*) AS n FROM coupon_uses WHERE code = 'WELCOME'").get().n,
  }));
  const lines = [`수량 1개 쿠폰에 동시 요청 10개`, `성공 ${ok}건, 남은 수량 ${remaining}, 사용 기록 ${uses}건`];
  if (ok > 1 || uses !== 1) return { red: true, lines, verdict: `쿠폰 한 장이 ${ok}번 쓰였습니다` };
  return { red: false, lines, verdict: '성공 1건, 나머지 9건은 409입니다' };
}
