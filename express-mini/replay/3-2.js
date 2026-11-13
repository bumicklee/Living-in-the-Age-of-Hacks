// 상품 검색어에 따옴표가 든 문자열을 넣는다.
import { request } from '../tools/http.js';

export default async function () {
  const normal = await request('GET', `/api/products?q=${encodeURIComponent('배터리')}`);
  const attack = await request('GET', `/api/products?q=${encodeURIComponent("%' OR 1=1 --")}`);
  const hidden = (attack.json ?? []).filter((p) => p.name.includes('비공개'));
  const lines = [`검색어 「배터리」: ${normal.json?.length ?? 0}개`, `검색어 「%' OR 1=1 --」: ${attack.json?.length ?? 0}개`];
  for (const p of hidden) lines.push(`비공개 상품: ${p.name}`);
  if (hidden.length > 0) return { red: true, lines, verdict: `검색어 하나로 비공개 상품 ${hidden.length}개가 나왔습니다` };
  return { red: false, lines, verdict: '따옴표가 든 검색어는 결과 0개, 정상 검색은 그대로입니다' };
}
