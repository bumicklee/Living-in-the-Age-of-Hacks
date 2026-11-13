import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: '모든 응답에 보안 헤더가 붙는다',
    hint: 'app.js의 라우터보다 앞에 kit.headers()를 연결했나요?',
    async run(h) {
      const res = await h.request('GET', '/api/health');
      expectStatus(res, 200, '상태 확인');
      expectTrue(res.headers.get('x-content-type-options') === 'nosniff', '보안 헤더가 없습니다');
      expectTrue(Boolean(res.headers.get('x-trace-id')), '추적 번호 헤더가 없습니다');
    },
  },
  {
    name: '로그인한 요청은 응답을 저장하지 않는다',
    hint: '인증 요청을 처리하기 전에 kit.noStoreAuthenticated()를 연결했나요?',
    async run(h) {
      const res = await h.request('GET', '/api/me', { token: await h.login('alice') });
      expectStatus(res, 200, '내 정보');
      expectTrue((res.headers.get('cache-control') ?? '').includes('no-store'), 'Cache-Control: no-store가 없습니다');
    },
  },
  {
    name: '큰 JSON 본문은 부품이 413으로 거절한다',
    hint: 'kit.bodyLimit()를 JSON 파서 앞에 두고 상한을 100kb로 맞췄나요?',
    async run(h) {
      const res = await h.request('POST', '/api/login', { body: { username: 'alice', password: 'x'.repeat(110_000) } });
      expectStatus(res, 413, '큰 본문');
      expectTrue(res.json?.code === 413, '부품의 오류 응답이 아닙니다');
    },
  },
  {
    name: '서버 오류는 부품이 가리고 로그에는 원인을 남긴다',
    hint: '맨 끝에 kit.errorResponse()를 연결하고 로그 출력을 app.log에 이어 주었나요?',
    async run(h) {
      const res = await h.request('POST', '/api/reviews', { token: await h.login('bob'), body: { productId: 1 } });
      expectStatus(res, 500, '본문 없는 리뷰');
      expectTrue(res.json?.code === 500 && Boolean(res.json?.traceId), '부품 오류 응답과 추적 번호가 없습니다');
      const log = h.readLog('app.log');
      expectTrue(log.includes(res.json.traceId) && log.includes('NOT NULL'), '같은 추적 번호의 원인 로그가 없습니다');
    },
  },
];
