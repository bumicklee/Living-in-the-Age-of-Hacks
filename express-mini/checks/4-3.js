import { expectStatus, expectTrue } from '../tools/expect.js';

async function tryLogin(h, username, password) {
  return h.request('POST', '/api/login', { body: { username, password } });
}

export default [
  {
    name: '틀린 비밀번호 5번까지는 401',
    async run(h) {
      for (let i = 1; i <= 5; i += 1) expectStatus(await tryLogin(h, 'carol', `wrong-${i}`), 401, `${i}번째 실패`);
    },
  },
  {
    name: '6번째부터는 맞는 비밀번호여도 429',
    hint: '아이디와 IP별로 실패 시각을 모아 15분 안에 5번이면 막나요? 막을 때는 비밀번호를 확인하기 전에 막나요?',
    async run(h) {
      for (let i = 1; i <= 5; i += 1) await tryLogin(h, 'carol', `wrong-${i}`);
      expectStatus(await tryLogin(h, 'carol', 'wrong-6'), 429, '6번째 시도');
      expectStatus(await tryLogin(h, 'carol', 'carol-1234'), 429, '막힌 뒤 맞는 비밀번호');
    },
  },
  {
    name: '다른 계정은 영향을 받지 않는다',
    async run(h) {
      for (let i = 1; i <= 6; i += 1) await tryLogin(h, 'carol', `wrong-${i}`);
      expectStatus(await tryLogin(h, 'alice', 'alice-1234'), 200, 'alice 로그인');
    },
  },
  {
    name: '15분이 지나면 다시 로그인할 수 있다',
    async run(h) {
      for (let i = 1; i <= 6; i += 1) await tryLogin(h, 'carol', `wrong-${i}`);
      const start = Date.now();
      h.clock.now = () => start + 16 * 60 * 1000;
      const res = await tryLogin(h, 'carol', 'carol-1234');
      expectTrue(res.status === 200, `16분 뒤 로그인이 ${res.status}입니다`);
    },
  },
];
