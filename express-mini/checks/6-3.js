import { expectTrue } from '../tools/expect.js';

async function startsWith(secret) {
  const { loadConfig } = await import('../app/config.js');
  try {
    loadConfig({ SHOP_SECRET: secret });
    return { ok: true };
  } catch (err) {
    return { ok: false, message: err.message };
  }
}

export default [
  {
    name: '비밀 값이 비어 있으면 시작하지 않는다',
    hint: 'loadConfig 에서 SHOP_SECRET이 없으면 오류를 던지나요?',
    noHarness: true,
    async run() {
      const r = await startsWith('');
      expectTrue(!r.ok, '비밀 값 없이 시작했습니다');
      expectTrue(r.message.includes('npm run secret'), `오류 문구에 해결 방법이 없습니다: ${r.message}`);
    },
  },
  {
    name: '널리 알려진 값(keyboard cat)이면 시작하지 않는다',
    noHarness: true,
    async run() {
      expectTrue(!(await startsWith('keyboard cat')).ok, '알려진 값으로 시작했습니다');
    },
  },
  {
    name: '32자보다 짧으면 시작하지 않는다',
    noHarness: true,
    async run() {
      expectTrue(!(await startsWith('short-secret-1234')).ok, '짧은 값으로 시작했습니다');
    },
  },
  {
    name: '충분히 긴 무작위 값이면 시작한다',
    noHarness: true,
    async run() {
      const r = await startsWith('k3vQ8mZp1LxW9tRb2NcY7uHf4JdS6gAe0oPq');
      expectTrue(r.ok, `시작하지 못했습니다: ${r.message}`);
    },
  },
];
