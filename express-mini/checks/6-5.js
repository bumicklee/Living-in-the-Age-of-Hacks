import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: '오류가 나도 응답에 DB 표·칸 이름과 스택이 없다',
    hint: '오류 처리기가 err.message·err.stack 대신 일반 문구와 추적 번호만 돌려주나요?',
    async run(h) {
      const res = await h.request('POST', '/api/reviews', { token: await h.login('bob'), body: { productId: 1 } });
      expectStatus(res, 500, '본문 없는 리뷰');
      expectTrue(!res.text.includes('reviews.body'), '응답에 표·칸 이름(reviews.body)이 보입니다');
      expectTrue(!res.text.includes('stack') && !res.text.includes('.js:'), '응답에 스택(파일 경로)이 보입니다');
      expectTrue(Boolean(res.json?.traceId), '응답에 추적 번호(traceId)가 없습니다');
    },
  },
  {
    name: '같은 추적 번호로 로그에서 원래 오류를 찾을 수 있다',
    async run(h) {
      const res = await h.request('POST', '/api/reviews', { token: await h.login('bob'), body: { productId: 1 } });
      const line = h.readLog('app.log').split('\n').find((l) => res.json?.traceId && l.includes(res.json.traceId));
      expectTrue(Boolean(line), '로그에 추적 번호가 없습니다');
      expectTrue(line.includes('NOT NULL'), '로그에 원래 오류 내용이 없습니다');
    },
  },
  {
    name: '잘못된 JSON 본문도 원문을 돌려주지 않는다',
    async run(h) {
      const res = await h.request('POST', '/api/login', { raw: '{"username": "bob", ' });
      expectTrue(res.status === 400, `응답 ${res.status}`);
      expectTrue(!res.text.includes('JSON at position') && !res.text.includes('stack'), '응답에 파싱 오류 원문이 보입니다');
    },
  },
  {
    name: '정상 요청은 그대로 성공',
    async run(h) {
      const res = await h.request('POST', '/api/reviews', { token: await h.login('bob'), body: { productId: 1, body: '좋아요' } });
      expectStatus(res, 201, '정상 리뷰');
    },
  },
];
