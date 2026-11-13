import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: '외부 확인이 실패하면 자료를 보내지 않는다',
    hint: 'catch에서 요청을 성공으로 끝내지 말고 거절 응답을 돌려주나요?',
    async run(h) {
      const res = await h.request('GET', '/api/partner/report');
      expectStatus(res, 503, '외부 확인 실패');
      expectTrue(!res.text.includes('고객 자료'), '확인에 실패했는데 자료가 나갔습니다');
    },
  },
  {
    name: '관련 없는 정상 요청은 계속 성공한다',
    async run(h) {
      expectStatus(await h.request('GET', '/api/health'), 200, '상태 확인');
    },
  },
];
