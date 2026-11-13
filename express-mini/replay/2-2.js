// bob으로 로그인해 alice의 주문 번호 1001을 요청한다.
import { login, request } from '../tools/http.js';

export default async function () {
  const res = await request('GET', '/api/orders/1001', { token: await login('bob') });
  const own = await request('GET', '/api/orders/2001', { token: await login('bob') });
  const lines = [`bob → 주문 1001(alice의 주문): ${res.status}`, `bob → 주문 2001(bob의 주문): ${own.status}`];
  if (res.status === 200) {
    lines.push(`응답에 보인 배송지: ${res.json.address}`);
    return { red: true, lines, verdict: 'bob이 alice의 주문 1001과 배송지를 봤습니다' };
  }
  return { red: false, lines, verdict: '남의 주문은 404, 내 주문은 200입니다' };
}
