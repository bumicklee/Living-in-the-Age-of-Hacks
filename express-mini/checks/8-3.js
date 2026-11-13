import { expectStatus, expectTrue } from '../tools/expect.js';

const auditLines = (h) => h.readLog('audit.log').split('\n').filter(Boolean).map((l) => JSON.parse(l));

export default [
  {
    name: '고객 목록 조회마다 감사 기록 1줄(누가·무엇을·몇 건)',
    hint: 'log.audit(req, 동작, { count }) 를 고객 목록 처리기에서 부르나요?',
    async run(h) {
      const token = await h.login('admin');
      expectStatus(await h.request('GET', '/api/admin/customers', { token }), 200, '고객 목록');
      const lines = auditLines(h);
      expectTrue(lines.length === 1, `감사 기록이 ${lines.length}줄입니다`);
      expectTrue(lines[0].actor === 'admin:5', `누가(actor): ${lines[0].actor}`);
      expectTrue(lines[0].action === 'customer.list' && lines[0].count === 3, `무엇을·몇 건: ${lines[0].action} ${lines[0].count}`);
    },
  },
  {
    name: '고객 한 명 조회도 대상과 함께 남는다',
    async run(h) {
      await h.request('GET', '/api/admin/customers/2', { token: await h.login('admin') });
      const line = auditLines(h).find((l) => l.action === 'customer.read');
      expectTrue(line?.target === 'customer:2', `대상: ${line?.target}`);
    },
  },
  {
    name: '감사 기록에는 전화번호·주소 같은 값이 없다',
    async run(h) {
      await h.request('GET', '/api/admin/customers', { token: await h.login('admin') });
      const text = h.readLog('audit.log');
      expectTrue(!text.includes('010-0000') && !text.includes('연습로'), '감사 기록에 개인 정보 값이 있습니다');
    },
  },
];
