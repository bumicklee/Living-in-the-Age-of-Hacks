// 직원(staff) 계정으로 관리자 전용 주소를 요청한다.
import { login, request } from '../tools/http.js';

export default async function () {
  const staff = await login('staff');
  const customers = await request('GET', '/api/admin/customers', { token: staff });
  const exported = await request('GET', '/api/admin/export', { token: staff });
  const orders = await request('GET', '/api/admin/orders', { token: staff });
  const lines = [
    `직원 → 고객 목록: ${customers.status}`,
    `직원 → 고객 내보내기(권한표에 없는 새 주소): ${exported.status}`,
    `직원 → 주문 목록(직원 업무): ${orders.status}`,
  ];
  if (customers.status === 200 || exported.status === 200) {
    const n = (customers.json?.length ?? 0) || (exported.json?.length ?? 0);
    return { red: true, lines, verdict: `직원이 관리자 전용 고객 정보 ${n}명을 봤습니다` };
  }
  return { red: false, lines, verdict: '직원은 고객 정보 403, 주문 목록은 200입니다' };
}
