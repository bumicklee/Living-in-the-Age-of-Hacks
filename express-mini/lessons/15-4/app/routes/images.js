// 상품 이미지를 주소로 가져와 확인한다.
// labNet 은 랩의 가짜 인터넷이다(lab-world). 실무의 dns.lookup·http 요청과 같은 모양이다.
import { Router } from 'express';
import { BlockList } from 'node:net';

// ✏️ 2-5: 연결하면 안 되는 내부 주소(루프백·사설·링크 로컬·IPv6 내부)
const INTERNAL = new BlockList();
for (const [net, bits] of [['0.0.0.0', 8], ['10.0.0.0', 8], ['100.64.0.0', 10], ['127.0.0.0', 8], ['169.254.0.0', 16], ['172.16.0.0', 12], ['192.168.0.0', 16]]) {
  INTERNAL.addSubnet(net, bits, 'ipv4');
}
// IPv4를 담은 IPv6 주소(::ffff:127.0.0.1 등)는 위의 IPv4 규칙으로 함께 걸러진다
for (const [net, bits] of [['::1', 128], ['fc00::', 7], ['fe80::', 10]]) INTERNAL.addSubnet(net, bits, 'ipv6');
const isInternal = (a) => INTERNAL.check(a.address, a.family === 6 ? 'ipv6' : 'ipv4');

export function imagesRouter({ config, auth, labNet }) {
  const r = Router();

  // ✏️ 2-5: 주소 방식 → 허용한 서버 → 해석한 모든 주소 → 그 주소로만 연결 → 형식 순서로 확인한다
  r.post('/products/:id/image', auth.requireLogin, async (req, res) => {
    const refuse = (message) => res.status(400).json({ message });
    let target;
    try {
      target = new URL(String(req.body?.imageUrl ?? ''));
    } catch {
      return refuse('주소 형식이 아닙니다');
    }
    if (target.protocol !== 'http:' && target.protocol !== 'https:') return refuse('http 또는 https 주소만 쓸 수 있습니다');
    if (!config.imageHosts.includes(target.hostname)) return refuse('허용한 이미지 서버의 주소만 쓸 수 있습니다');

    const addresses = await labNet.lookup(target.hostname);
    if (addresses.length === 0 || addresses.some(isInternal)) return refuse('내부 주소로는 연결하지 않습니다');

    let resp;
    try {
      // 검사한 주소로만 연결한다. 다른 곳으로 보내도 따라가지 않고, 3초·1MB를 넘으면 끊는다.
      resp = await labNet.request(target, { address: addresses[0].address, timeoutMs: 3000, maxBytes: 1024 * 1024 });
    } catch {
      return res.status(502).json({ message: '이미지를 가져오지 못했습니다' });
    }
    const type = String(resp.headers['content-type'] ?? '');
    if (resp.status !== 200 || !type.startsWith('image/')) return refuse('이미지가 아닙니다');
    res.json({ status: resp.status, contentType: type, size: resp.body.length });
  });

  return r;
}
