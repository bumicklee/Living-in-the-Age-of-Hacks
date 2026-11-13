import { expectStatus, expectTrue } from '../tools/expect.js';

const ATTACK = '<img src=x onerror="alert(1)">좋아요<script>alert(2)</script>';

async function postReview(h, body) {
  return h.request('POST', '/api/reviews', { token: await h.login('bob'), body: { productId: 1, body } });
}

export default [
  {
    name: '정상 리뷰 글자는 그대로 보인다',
    async run(h) {
      await postReview(h, '배송이 빨라요 & 포장도 좋아요');
      const page = await h.request('GET', '/products/1');
      expectStatus(page, 200, '상품 화면');
      expectTrue(page.text.includes('배송이 빨라요 &amp; 포장도 좋아요') || page.text.includes('배송이 빨라요 & 포장도 좋아요'), '정상 리뷰가 보이지 않습니다');
    },
  },
  {
    name: '리뷰 속 태그는 글자로 바뀌어 들어간다(출력 인코딩)',
    hint: '리뷰를 HTML에 넣기 전에 < > & " \' 를 글자로 바꾸나요?',
    async run(h) {
      await postReview(h, ATTACK);
      const page = await h.request('GET', '/products/1');
      expectTrue(!page.text.includes('<script>alert(2)</script>'), '스크립트 태그가 HTML에 그대로 있습니다');
      expectTrue(!page.text.includes('<img src=x'), '이미지 태그가 HTML에 그대로 있습니다');
      expectTrue(page.text.includes('&lt;script&gt;'), '인코딩된 글자(&lt;script&gt;)가 보이지 않습니다');
    },
  },
  {
    name: '상품 화면에 CSP 헤더가 있다(스크립트 실행 금지)',
    hint: "Content-Security-Policy 헤더에 script-src 'none' 이 있나요?",
    async run(h) {
      const page = await h.request('GET', '/products/1');
      const csp = page.headers.get('content-security-policy') ?? '';
      expectTrue(csp.includes("script-src 'none'"), `CSP 헤더: ${csp || '(없음)'}`);
    },
  },
];
