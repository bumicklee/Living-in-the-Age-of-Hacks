import { request } from '../tools/http.js';

export default async function () {
  const res = await request('GET', '/api/partner/report');
  const leaked = res.text.includes('고객 자료');
  return {
    red: leaked,
    lines: [`외부 확인 실패 뒤 응답: ${res.status}`, `고객 자료 포함: ${leaked ? '예' : '아니요'}`],
    verdict: leaked ? '확인 실패를 숨기고 고객 자료를 보냈습니다' : '확인 실패 때 자료를 보내지 않았습니다',
  };
}
