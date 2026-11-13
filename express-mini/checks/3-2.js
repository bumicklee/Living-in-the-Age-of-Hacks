import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: '정상 검색: 「배터리」로 보조 배터리 1개',
    async run(h) {
      const res = await h.request('GET', `/api/products?q=${encodeURIComponent('배터리')}`);
      expectStatus(res, 200, '검색');
      expectTrue(res.json?.length === 1, `검색 결과가 ${res.json?.length}개입니다`);
    },
  },
  {
    name: '빈 검색어는 공개 상품 3개',
    async run(h) {
      const res = await h.request('GET', '/api/products');
      expectTrue(res.json?.length === 3, `공개 상품이 ${res.json?.length}개입니다`);
    },
  },
  {
    name: '따옴표가 든 검색어로 비공개 상품이 나오지 않는다',
    hint: '검색어를 SQL 문자열에 붙이지 말고 자리표시자(?)로 넘기나요?',
    async run(h) {
      const res = await h.request('GET', `/api/products?q=${encodeURIComponent("%' OR 1=1 --")}`);
      expectTrue(res.status === 200, `응답 ${res.status}`);
      expectTrue(!res.text.includes('비공개'), '비공개 상품이 응답에 보입니다');
      expectTrue(res.json?.length === 0, `결과가 ${res.json?.length}개입니다(0개여야 함)`);
    },
  },
];
