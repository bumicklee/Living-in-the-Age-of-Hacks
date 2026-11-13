import { expectStatus, expectTrue } from '../tools/expect.js';

export default [
  {
    name: 'bob은 자기 이름을 바꿀 수 있다',
    async run(h) {
      const token = await h.login('bob');
      expectStatus(await h.request('PATCH', '/api/me', { token, body: { name: '밥2' } }), 200, '이름 바꾸기');
      const me = await h.request('GET', '/api/me', { token });
      expectTrue(me.json?.name === '밥2', `이름이 바뀌지 않았습니다: ${me.json?.name}`);
    },
  },
  {
    name: '본문에 role을 넣어도 역할은 그대로 customer',
    hint: '바꿀 수 있는 칸을 이름·전화·주소로 정해 두고 그 밖의 칸은 무시하나요?',
    async run(h) {
      const token = await h.login('bob');
      await h.request('PATCH', '/api/me', { token, body: { name: '밥', role: 'admin' } });
      const me = await h.request('GET', '/api/me', { token });
      expectTrue(me.json?.role === 'customer', `역할이 ${me.json?.role}(으)로 바뀌었습니다`);
    },
  },
  {
    name: '본문에 password_hash·username을 넣어도 바뀌지 않는다',
    async run(h) {
      const token = await h.login('bob');
      await h.request('PATCH', '/api/me', { token, body: { username: 'admin2', password_hash: 'x' } });
      expectTrue(Boolean(await h.login('bob')), 'bob으로 다시 로그인할 수 없습니다(아이디나 비밀번호가 바뀜)');
    },
  },
];
