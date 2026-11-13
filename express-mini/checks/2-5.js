import { expectStatus, expectTrue } from '../tools/expect.js';

async function fetchImage(h, imageUrl) {
  return h.request('POST', '/api/products/1/image', { token: await h.login('bob'), body: { imageUrl } });
}
const noSecret = (res) => expectTrue(!res.text.includes('LAB-FAKE-KEY'), '사내 관리 서버의 값이 응답에 보입니다');

export default [
  {
    name: '허용한 이미지 서버의 이미지는 가져온다',
    async run(h) {
      const res = await fetchImage(h, 'http://images.daon.example/cat.png');
      expectStatus(res, 200, '정상 이미지');
      expectTrue(res.json?.contentType?.startsWith('image/'), '이미지 형식이 아닙니다');
    },
  },
  {
    name: '사내 관리 서버 주소(127.0.0.1·localhost·내부 이름)는 거부',
    hint: '허용한 이미지 서버(config.imageHosts)인지 먼저 확인하나요?',
    async run(h) {
      for (const host of ['127.0.0.1', 'localhost', 'inside.daon.example']) {
        const res = await fetchImage(h, `http://${host}:${h.world.internalPort}/admin/secrets`);
        expectStatus(res, 400, `${host} 주소`);
        noSecret(res);
      }
    },
  },
  {
    name: 'http·https 말고 다른 주소 방식은 거부',
    async run(h) {
      expectStatus(await fetchImage(h, 'file:///etc/passwd'), 400, 'file: 주소');
    },
  },
  {
    name: '이미지 서버가 내부로 보내도(리다이렉트) 따라가지 않는다',
    hint: '한 번만 요청하고 3xx 응답은 이미지가 아닌 것으로 거부하나요?',
    async run(h) {
      const res = await fetchImage(h, `http://images.daon.example/go?to=http://inside.daon.example:${h.world.internalPort}/admin/secrets`);
      expectStatus(res, 400, '리다이렉트');
      noSecret(res);
    },
  },
  {
    name: '허용 목록에 있어도 해석한 주소 중 하나라도 내부면 거부',
    hint: 'labNet.lookup 결과의 모든 주소를 내부 주소 목록과 비교하나요?',
    env: { SHOP_IMAGE_HOSTS: 'images.daon.example,inside.daon.example,mixed.daon.example' },
    async run(h) {
      for (const host of ['inside.daon.example', 'mixed.daon.example']) {
        const res = await fetchImage(h, `http://${host}:${h.world.internalPort}/admin/secrets`);
        expectStatus(res, 400, `${host}(내부로 해석)`);
        noSecret(res);
      }
    },
  },
  {
    name: '검사한 주소로만 연결한다(검사 뒤 다시 해석하지 않는다)',
    hint: '검사에 쓴 주소를 labNet.request 의 address 로 넘기나요?',
    env: { SHOP_IMAGE_HOSTS: 'images.daon.example,flip.daon.example' },
    async run(h) {
      const res = await fetchImage(h, `http://flip.daon.example:${h.world.internalPort}/admin/secrets`);
      noSecret(res);
      expectTrue(res.status >= 400, `응답 ${res.status}: 내부 서버에 닿았을 수 있습니다`);
    },
  },
  {
    name: '이미지가 아닌 문서·너무 큰 응답·너무 늦은 응답은 거부',
    async run(h) {
      expectStatus(await fetchImage(h, 'http://images.daon.example/page.html'), 400, '문서 형식');
      const big = await fetchImage(h, 'http://images.daon.example/big.png');
      expectTrue(big.status >= 400, `2MB 응답이 ${big.status}로 통과했습니다`);
      const slow = await fetchImage(h, 'http://images.daon.example/slow.png');
      expectTrue(slow.status >= 400, `5초 걸리는 응답이 ${slow.status}로 통과했습니다`);
    },
  },
];
