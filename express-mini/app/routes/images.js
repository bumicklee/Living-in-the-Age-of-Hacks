// 상품 이미지를 주소로 가져와 확인한다.
// labNet 은 랩의 가짜 인터넷이다(lab-world). 실무의 dns.lookup·http 요청과 같은 모양이다.
import { Router } from 'express';

export function imagesRouter({ auth, labNet }) {
  const r = Router();

  // ✏️ 2-5: 받은 주소를 확인하지 않고 그대로 가져온다(다른 곳으로 보내면 따라간다)
  r.post('/products/:id/image', auth.requireLogin, async (req, res) => {
    const url = String(req.body?.imageUrl ?? '');
    const resp = await labNet.fetch(url);
    res.json({
      status: resp.status,
      contentType: resp.headers['content-type'] ?? null,
      size: resp.body.length,
      preview: resp.body.subarray(0, 200).toString('utf8'),
    });
  });

  return r;
}
