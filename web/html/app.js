// 다온마켓 고객 웹(A7). 빌드 없는 바닐라 JS. 모든 데이터는 /api/ → mall-portal(고객 API)에서 온다.
// 화면 주소: /  /login  /cart  /orders  /orders/<주문 id>   (주문 id가 주소에 그대로 보인다: 2-2 수업의 출발점)
'use strict';

const API = '/api';
const TOKEN_KEY = 'daon.token';
const STATUS = { 0: '결제 대기', 1: '배송 대기', 2: '발송됨', 3: '완료', 4: '닫힘', 5: '무효' };
const app = document.getElementById('app');

// ── 작은 도우미 ──
function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'testid') el.dataset.testid = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : document.createTextNode(String(c)));   // 글자는 항상 textContent로
  }
  return el;
}
const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;
// API 시각(UTC ISO)을 한국 시각 'YYYY-MM-DD HH:mm'으로
function when(s) {
  const d = new Date(s);
  if (!s || Number.isNaN(d.getTime())) return s || '';
  const k = new Date(d.getTime() + 9 * 3600 * 1000).toISOString();
  return `${k.slice(0, 10)} ${k.slice(11, 16)}`;
}
const token = () => localStorage.getItem(TOKEN_KEY);

function toast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { t.hidden = true; }, 2500);
}

class ApiError extends Error {
  constructor(message, code) { super(message); this.code = code; }
}

async function api(path, { method = 'GET', json, form } = {}) {
  const headers = {};
  if (token()) headers.Authorization = token();
  let body;
  if (json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
  if (form) { body = new URLSearchParams(form); }
  const r = await fetch(API + path, { method, headers, body });
  let data;
  try { data = await r.json(); } catch { data = { code: r.status, message: `응답을 읽지 못했습니다(HTTP ${r.status})` }; }
  if (r.status === 401 || data.code === 401) {
    localStorage.removeItem(TOKEN_KEY);
    throw new ApiError('로그인이 필요합니다', 401);
  }
  if (data.code !== 200) throw new ApiError(data.message || `요청 실패(HTTP ${r.status})`, data.code);
  return data.data;
}

// ── 화면 이동 ──
function go(path) {
  history.pushState({}, '', path);
  route();
}
document.addEventListener('click', (e) => {
  const a = e.target.closest('a[data-link]');
  if (a && a.origin === location.origin) { e.preventDefault(); go(a.pathname); }
});
window.addEventListener('popstate', route);

function page(testid, ...children) {
  app.replaceChildren(h('section', { testid }, ...children));
}

async function route() {
  const p = location.pathname.replace(/\/+$/, '') || '/';
  await refreshHeader();
  try {
    if (p === '/') return await productsPage();
    if (p === '/login') return loginPage();
    if (p === '/cart') return await cartPage();
    if (p === '/orders') return await ordersPage();
    const m = p.match(/^\/orders\/([^/]+)$/);
    if (m) return await orderDetailPage(decodeURIComponent(m[1]));
    page('page-not-found', h('h1', {}, '없는 화면입니다'), h('a', { href: '/', 'data-link': true }, '상품 목록으로'));
  } catch (e) {
    if (e.code === 401) { await refreshHeader(); return loginPage('로그인이 필요합니다'); }
    page('page-error', h('h1', {}, '문제가 생겼습니다'), h('p', { class: 'error', testid: 'page-error-message' }, e.message));
  }
}

async function refreshHeader() {
  const who = document.getElementById('who');
  const login = document.getElementById('nav-login');
  const logout = document.getElementById('nav-logout');
  const count = document.getElementById('cart-count');
  if (!token()) {
    who.textContent = ''; count.textContent = '';
    login.hidden = false; logout.hidden = true;
    return;
  }
  try {
    const [me, cart] = await Promise.all([api('/sso/info'), api('/cart/list')]);
    who.textContent = `${me.nickname || me.username}님`;
    count.textContent = cart.length ? String(cart.length) : '';
    login.hidden = true; logout.hidden = false;
  } catch {
    who.textContent = ''; count.textContent = '';
    login.hidden = !!token(); logout.hidden = !token();
  }
}
document.getElementById('nav-logout').addEventListener('click', () => {
  localStorage.removeItem(TOKEN_KEY);
  toast('로그아웃했습니다');
  go('/');
});

// ── 로그인 ──
function loginPage(message) {
  const err = h('p', { class: 'error', testid: 'login-error' }, message || '');
  const form = h('form', { class: 'login', testid: 'login-form' },
    h('label', {}, '아이디', h('input', { name: 'username', autocomplete: 'username', required: true, testid: 'login-username' })),
    h('label', {}, '비밀번호', h('input', { name: 'password', type: 'password', autocomplete: 'current-password', required: true, testid: 'login-password' })),
    err,
    h('button', { type: 'submit', testid: 'login-submit' }, '로그인'),
    h('p', { class: 'muted' }, '가짜 계정: alice / bob, 비밀번호 daon-pass-1!'));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    try {
      const d = await api('/sso/login', { method: 'POST', form: { username: form.username.value, password: form.password.value } });
      localStorage.setItem(TOKEN_KEY, d.tokenHead + d.token);
      toast('로그인했습니다');
      go('/');
    } catch (ex) {
      err.textContent = ex.message;
    }
  });
  page('page-login', h('h1', {}, '로그인'), form);
}

// ── 상품 ──
async function productsPage() {
  const d = await api('/product/search?brandId=101&pageNum=1&pageSize=60&sort=0');
  const cards = d.list.map((p) => h('article', { class: 'card', testid: `product-card-${p.id}` },
    h('div', { class: 'thumb', 'aria-hidden': 'true' }, p.name.replace(/^다온\s*/, '').slice(0, 1)),
    h('h2', { testid: `product-name-${p.id}` }, p.name),
    h('div', { class: 'muted' }, p.subTitle || ''),
    h('div', { class: 'price', testid: `product-price-${p.id}` }, won(p.price)),
    h('button', { type: 'button', testid: `add-to-cart-${p.id}`, onclick: () => addToCart(p.id) }, '장바구니 담기')));
  page('page-products', h('h1', {}, '상품'), h('div', { class: 'grid', testid: 'product-list' }, cards));
}

async function addToCart(productId) {
  if (!token()) return go('/login');
  try {
    const d = await api(`/product/detail/${productId}`);
    const p = d.product;
    const sku = (d.skuStockList || [])[0];
    let attr = '';
    try { attr = sku && sku.spData ? sku.spData : ''; } catch { attr = ''; }
    await api('/cart/add', {
      method: 'POST',
      json: {
        productId: p.id, productSkuId: sku ? sku.id : null, quantity: 1,
        price: sku ? sku.price : p.price, productName: p.name, productSubTitle: p.subTitle,
        productSkuCode: sku ? sku.skuCode : null, productBrand: p.brandName, productSn: p.productSn,
        productCategoryId: p.productCategoryId, productAttr: attr, productPic: p.pic,
      },
    });
    toast(`${p.name}을(를) 담았습니다`);
    refreshHeader();
  } catch (e) {
    if (e.code === 401) return go('/login');
    toast(e.message);
  }
}

// ── 장바구니 → 주문 ──
async function cartPage() {
  if (!token()) return loginPage('로그인하면 장바구니를 볼 수 있습니다');
  const [items, addresses] = await Promise.all([api('/cart/list'), api('/member/address/list')]);
  if (!items.length) {
    return page('page-cart', h('h1', {}, '장바구니'), h('p', { class: 'muted', testid: 'cart-empty' }, '장바구니가 비어 있습니다.'),
      h('a', { href: '/', 'data-link': true }, '상품 보러 가기'));
  }
  const total = items.reduce((s, i) => s + i.price * i.quantity, 0);
  const rows = items.map((i) => h('tr', { testid: `cart-row-${i.id}` },
    h('td', {}, i.productName),
    h('td', { class: 'num' }, won(i.price)),
    h('td', { class: 'num', testid: `cart-qty-${i.id}` }, i.quantity),
    h('td', { class: 'num' }, won(i.price * i.quantity)),
    h('td', { class: 'num' }, h('button', { type: 'button', class: 'secondary', testid: `cart-remove-${i.id}`, onclick: async () => {
      await api(`/cart/delete?ids=${i.id}`, { method: 'POST' });
      route();
    } }, '빼기'))));
  const select = h('select', { testid: 'order-address' },
    addresses.map((a) => h('option', { value: a.id, selected: a.defaultStatus === 1 }, `${a.name} · ${a.province} ${a.city} ${a.region} ${a.detailAddress}`)));
  const submit = h('button', { type: 'button', testid: 'order-submit' }, '주문하기');
  submit.addEventListener('click', async () => {
    if (!select.value) return toast('배송지가 없습니다');
    submit.disabled = true;
    try {
      const d = await api('/order/generateOrder', {
        method: 'POST',
        json: { cartIds: items.map((i) => i.id), memberReceiveAddressId: Number(select.value), payType: 0 },
      });
      toast(`주문했습니다: ${d.order.orderSn}`);
      go(`/orders/${d.order.id}`);
    } catch (e) {
      submit.disabled = false;
      toast(e.message);
    }
  });
  page('page-cart', h('h1', {}, '장바구니'),
    h('table', { testid: 'cart-table' },
      h('thead', {}, h('tr', {}, h('th', {}, '상품'), h('th', { class: 'num' }, '가격'), h('th', { class: 'num' }, '수량'), h('th', { class: 'num' }, '합계'), h('th', {}, ''))),
      h('tbody', {}, rows)),
    h('div', { class: 'actions' },
      h('label', {}, '배송지', select),
      h('strong', { testid: 'cart-total' }, `합계 ${won(total)}`),
      submit));
}

// ── 내 주문 ──
async function ordersPage() {
  if (!token()) return loginPage('로그인하면 주문을 볼 수 있습니다');
  const d = await api('/order/list?status=-1&pageNum=1&pageSize=50');
  if (!d.list.length) return page('page-orders', h('h1', {}, '내 주문'), h('p', { class: 'muted', testid: 'orders-empty' }, '주문이 없습니다.'));
  const rows = d.list.map((o) => h('tr', { testid: `order-row-${o.id}` },
    h('td', {}, h('a', { href: `/orders/${o.id}`, 'data-link': true, testid: `order-link-${o.id}` }, o.orderSn)),
    h('td', {}, when(o.createTime)),
    h('td', {}, (o.orderItemList || []).map((i) => i.productName).join(', ')),
    h('td', { class: 'num' }, won(o.payAmount)),
    h('td', {}, h('span', { class: 'badge' }, STATUS[o.status] || o.status))));
  page('page-orders', h('h1', {}, '내 주문'),
    h('table', { testid: 'orders-table' },
      h('thead', {}, h('tr', {}, h('th', {}, '주문 번호'), h('th', {}, '주문일'), h('th', {}, '상품'), h('th', { class: 'num' }, '결제 금액'), h('th', {}, '상태'))),
      h('tbody', {}, rows)));
}

async function orderDetailPage(id) {
  if (!token()) return loginPage('로그인하면 주문을 볼 수 있습니다');
  const o = await api(`/order/detail/${encodeURIComponent(id)}`);
  const field = (label, testid, value) => [h('dt', {}, label), h('dd', { testid }, value)];
  const pay = o.status === 0
    ? h('button', { type: 'button', testid: 'order-pay', onclick: async () => {
      await api('/order/paySuccess', { method: 'POST', form: { orderId: o.id, payType: 1 } });
      toast('가짜 결제를 마쳤습니다');
      route();
    } }, '가짜 결제하기')
    : null;
  const items = (o.orderItemList || []).map((i) => h('tr', {},
    h('td', {}, i.productName), h('td', { class: 'num' }, won(i.productPrice)), h('td', { class: 'num' }, i.productQuantity)));
  page('page-order-detail', h('h1', {}, '주문 상세'),
    h('dl', { class: 'detail', testid: 'order-detail' },
      field('주문 번호', 'order-sn', o.orderSn),
      field('상태', 'order-status', STATUS[o.status] || o.status),
      field('주문일', 'order-created', when(o.createTime)),
      field('받는 사람', 'order-receiver-name', o.receiverName),
      field('전화', 'order-receiver-phone', o.receiverPhone),
      field('주소', 'order-receiver-address', [o.receiverProvince, o.receiverCity, o.receiverRegion, o.receiverDetailAddress].filter(Boolean).join(' ')),
      field('택배', 'order-delivery', o.deliverySn ? `${o.deliveryCompany || ''} ${o.deliverySn}` : '-'),
      field('결제 금액', 'order-pay-amount', won(o.payAmount))),
    h('table', { testid: 'order-items' },
      h('thead', {}, h('tr', {}, h('th', {}, '상품'), h('th', { class: 'num' }, '가격'), h('th', { class: 'num' }, '수량'))),
      h('tbody', {}, items)),
    h('div', { class: 'actions' }, pay, h('a', { href: '/orders', 'data-link': true, testid: 'order-back' }, '내 주문으로')));
}

route();
