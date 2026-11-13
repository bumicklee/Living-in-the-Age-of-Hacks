// 상품 검색과 상품 화면(리뷰 포함).
import { Router } from 'express';

// ✏️ 3-4: HTML에 넣기 전에 특수 문자를 글자로 바꾼다
const escapeHtml = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

export function productsRouter({ db }) {
  const r = Router();

  // 검색어는 SQL 문장에 붙이지 않고 자리표시자로 넘긴다.
  r.get('/api/products', (req, res) => {
    const q = String(req.query.q ?? '');
    const rows = db.prepare("SELECT id, name, price FROM products WHERE hidden = 0 AND name LIKE '%' || ? || '%' ORDER BY id").all(q);
    res.json(rows);
  });

  r.get('/products/:id', (req, res) => {
    const product = db.prepare('SELECT id, name, price FROM products WHERE id = ? AND hidden = 0').get(Number(req.params.id));
    if (!product) return res.status(404).send('상품이 없습니다');
    const reviews = db.prepare('SELECT body FROM reviews WHERE product_id = ? ORDER BY id').all(product.id);
    const items = reviews.map((rv) => `<li>${escapeHtml(rv.body)}</li>`).join('\n');
    // 놓친 곳이 있어도 페이지 안의 스크립트가 돌지 않게 한 번 더 막는다
    res.set('Content-Security-Policy', "default-src 'self'; script-src 'none'; object-src 'none'; base-uri 'none'");
    res.type('html').send(`<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><title>다온마켓 · 상품</title></head>
<body><h1>${escapeHtml(product.name)}</h1><p>${product.price.toLocaleString('ko-KR')}원</p>
<h2>리뷰</h2><ul>
${items}
</ul></body></html>`);
  });

  return r;
}
