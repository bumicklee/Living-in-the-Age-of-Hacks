// 상품 이미지 주소에 사내 관리 서버 주소를 넣는다(곧바로, 그리고 이미지 서버의 리다이렉트를 거쳐).
import { login, request } from '../tools/http.js';

const INTERNAL = `http://inside.daon.example:${process.env.LAB_INTERNAL_PORT ?? 4399}/admin/secrets`;

export default async function () {
  const token = await login('bob');
  const send = (imageUrl) => request('POST', '/api/products/1/image', { token, body: { imageUrl } });
  const direct = await send(INTERNAL);
  const viaRedirect = await send(`http://images.daon.example/go?to=${encodeURIComponent(INTERNAL)}`);
  const normal = await send('http://images.daon.example/cat.png');
  const leaked = [direct, viaRedirect].filter((r) => r.text.includes('LAB-FAKE-KEY')).length;
  const lines = [
    `사내 관리 서버 주소를 곧바로: ${direct.status}`,
    `이미지 서버의 리다이렉트를 거쳐: ${viaRedirect.status}`,
    `정상 이미지(cat.png): ${normal.status} ${normal.json?.contentType ?? ''}`,
  ];
  if (leaked > 0) return { red: true, lines, verdict: `사내 관리 서버의 가짜 비밀 값이 응답 ${leaked}건에 보였습니다` };
  return { red: false, lines, verdict: '내부 주소와 리다이렉트는 거부, 정상 이미지는 200입니다' };
}
