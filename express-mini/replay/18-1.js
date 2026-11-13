import { request } from '../tools/http.js';

export default async function () {
  const res = await request('GET', '/api/admin/orders');
  return {
    red: res.status === 200,
    lines: [`로그인 없이 관리자 주문 목록 요청: ${res.status}`],
    verdict: res.status === 200 ? '로그인 검사를 지나지 않고 관리자 주문 목록이 열렸습니다' : '로그인하지 않은 요청은 거절됐습니다',
  };
}
