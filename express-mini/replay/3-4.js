// 스크립트 태그가 든 리뷰를 남기고 상품 화면 HTML을 받아 본다. 지난 재연에서 남긴 리뷰는 먼저 지운다.
import { login, request, BASE_URL } from '../tools/http.js';
import { withDb } from '../tools/dbtool.js';

const ATTACK = '좋아요<script>alert("리뷰 스크립트")</script>';

export default async function () {
  withDb((db) => db.prepare("DELETE FROM reviews WHERE body LIKE '%<script>%'").run());
  await request('POST', '/api/reviews', { token: await login('bob'), body: { productId: 1, body: ATTACK } });
  const page = await request('GET', '/products/1');
  const raw = page.text.includes('<script>alert');
  const csp = page.headers.get('content-security-policy');
  const lines = [
    `리뷰에 스크립트 태그를 넣어 저장했습니다`,
    `상품 화면 HTML에 태그가 그대로 있음: ${raw ? '예' : '아니요(글자로 바뀜)'}`,
    `CSP 헤더: ${csp ?? '없음'}`,
    `브라우저로 직접 확인: ${BASE_URL}/products/1`,
  ];
  if (raw || !csp) return { red: true, lines, verdict: '리뷰의 스크립트가 HTML에 그대로 들어가 브라우저에서 실행될 수 있습니다' };
  return { red: false, lines, verdict: '리뷰는 글자로 들어가고 CSP 헤더가 있습니다(실행 여부는 브라우저 화면으로 확인)' };
}
