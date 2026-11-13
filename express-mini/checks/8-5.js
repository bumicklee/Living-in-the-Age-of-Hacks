import { expectTrue } from '../tools/expect.js';

export default [
  {
    name: '로그인 뒤 로그에 비밀번호가 남지 않는다',
    hint: '요청 기록에 넣기 전에 password·token·secret·phone 이 들어간 칸의 값을 가리나요?',
    async run(h) {
      await h.login('alice');
      expectTrue(!h.readLog('app.log').includes('alice-1234'), '로그에 비밀번호(alice-1234)가 있습니다');
    },
  },
  {
    name: '내 정보 수정 뒤 로그에 전화번호가 남지 않는다',
    async run(h) {
      const token = await h.login('bob');
      await h.request('PATCH', '/api/me', { token, body: { phone: '010-0000-9999' } });
      expectTrue(!h.readLog('app.log').includes('010-0000-9999'), '로그에 전화번호가 있습니다');
    },
  },
  {
    name: '요청 기록 자체는 남는다(무엇을 했는지는 보인다)',
    async run(h) {
      await h.login('alice');
      const line = h.readLog('app.log').split('\n').find((l) => l.includes('/api/login'));
      expectTrue(Boolean(line), '로그인 요청 기록이 없습니다');
      expectTrue(line.includes('alice'), '아이디까지 가려졌습니다(아이디는 남아야 합니다)');
    },
  },
];
