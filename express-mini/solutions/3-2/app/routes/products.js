// 상품 검색과 상품 화면(리뷰 포함).
import { Router } from 'express';

export function productsRouter({ db }) {
  const r = Router();

  // ✏️ 3-2: 검색어는 자리표시자(?)로 따로 넘긴다. SQL 문장과 값이 섞이지 않는다.
  r.get('/api/products', (req, res) => {
    const q = String(req.query.q ?? '');
    const rows = db.prepare("SELECT id, name, price FROM products WHERE hidden = 0 AND name LIKE '%' || ? || '%' ORDER BY id").all(q);
    res.json(rows);
  });

  // ✏️ 3-4: 리뷰 글을 HTML에 그대로 넣는다
  r.get('/products/:id', (req, res) => {
    const product = db.prepare('SELECT id, name, price FROM products WHERE id = ? AND hidden = 0').get(Number(req.params.id));
    if (!product) return res.status(404).send('상품이 없습니다');
    const reviews = db.prepare('SELECT body FROM reviews WHERE product_id = ? ORDER BY id').all(product.id);
    const items = reviews.map((rv) => `<li>${rv.body}</li>`).join('\n');
    res.type('html').send(`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>다온마켓 · 상품</title></head>
<body><h1>${product.name}</h1><p>${product.price.toLocaleString('ko-KR')}원</p>
<h2>리뷰</h2><ul>
${items}
</ul></body></html>`);
  });

  return r;
}
