import { expectStatus } from '../tools/expect.js';

export default [
  {
    name: '관리자는 고객 목록을 본다',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/customers', { token: await h.login('admin') }), 200, '관리자의 고객 목록');
    },
  },
  {
    name: '직원은 고객 목록을 볼 수 없다(403)',
    hint: '권한표(PERMISSIONS)에 /api/admin/customers 를 관리자만 들어오게 올렸나요?',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/customers', { token: await h.login('staff') }), 403, '직원의 고객 목록');
    },
  },
  {
    name: '권한표에 없는 새 주소(/api/admin/export)는 누구에게나 403',
    hint: 'authorize 에서 권한표에 없는 주소를 통과시키지 말고 거부하나요(기본 거부)?',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/export', { token: await h.login('staff') }), 403, '직원의 내보내기');
      expectStatus(await h.request('GET', '/api/admin/export', { token: await h.login('admin') }), 403, '관리자의 내보내기(아직 등록 전)');
    },
  },
  {
    name: '직원은 주문 목록을 계속 본다(정상 업무)',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/orders', { token: await h.login('staff') }), 200, '직원의 주문 목록');
    },
  },
  {
    name: '고객은 관리자 주소에 들어올 수 없다',
    async run(h) {
      expectStatus(await h.request('GET', '/api/admin/orders', { token: await h.login('bob') }), 403, '고객의 주문 목록');
    },
  },
];
