// 다온마켓 고객 웹(A7). 빌드 없는 바닐라 JS. 모든 데이터는 /api/ → mall-portal(고객 API)에서 온다.
// 화면 주소: /  /login  /signup  /reset  /cart  /orders  /orders/<주문 id>   (주문 id가 주소에 그대로 보인다: 2-2 수업의 출발점)
'use strict';

const API = '/api';
const TOKEN_KEY = 'daon.token';
const REFRESH_KEY = 'daon.refresh';   // 4-8: 갱신 토큰(서버가 줄 때만). 3-5 쿠키 수업과 맞춰 HttpOnly 쿠키로 옮기는 안은 수업에서 다룬다
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

// 4-3 기기 축: 처음 온 브라우저에 무작위 기기 ID(daon_did) 쿠키를 하나 준다. 로그인 속도 제한이 기기마다 센다
if (!/(?:^|;\s*)daon_did=/.test(document.cookie)) {
  const did = (crypto.randomUUID ? crypto.randomUUID() : String(Math.random()).slice(2) + Date.now());
  document.cookie = `daon_did=${did}; path=/; max-age=31536000; SameSite=Lax`;
}

function saveTokens(d) {
  localStorage.setItem(TOKEN_KEY, d.tokenHead + d.token);
  if (d.refreshToken) localStorage.setItem(REFRESH_KEY, d.refreshToken);
  else localStorage.removeItem(REFRESH_KEY);
}

function clearTokens() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

// 4-8: 액세스 토큰이 끝나면 갱신 토큰으로 한 번만 새로 받는다(갱신 토큰이 없으면 다시 로그인)
async function tryRefresh() {
  const rt = localStorage.getItem(REFRESH_KEY);
  if (!rt) return false;
  try {
    const r = await fetch(API + '/sso/refreshToken', { method: 'POST', body: new URLSearchParams({ refreshToken: rt }) });
    const data = await r.json();
    if (data.code !== 200 || !data.data) return false;
    saveTokens(data.data);
    return true;
  } catch {
    return false;
  }
}

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

async function api(path, { method = 'GET', json, form, retried } = {}) {
  const headers = {};
  if (token()) headers.Authorization = token();
  let body;
  if (json !== undefined) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
  if (form) { body = new URLSearchParams(form); }
  const r = await fetch(API + path, { method, headers, body });
  let data;
  try { data = await r.json(); } catch { data = { code: r.status, message: `응답을 읽지 못했습니다(HTTP ${r.status})` }; }
  if (r.status === 401 || data.code === 401) {
    if (!retried && token() && await tryRefresh()) return api(path, { method, json, form, retried: true });
    clearTokens();
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
    const rv = p.match(/^\/reviews\/new\/(\d+)$/);
    if (rv) return await reviewPage(Number(rv[1]));
    if (p === '/login') return loginPage();
    if (p === '/signup') return signupPage();
    if (p === '/reset') return resetPage();
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
document.getElementById('nav-logout').addEventListener('click', async () => {
  // 서버에도 로그아웃을 알린다(4-8에서 토큰을 서버 쪽에서 끝낸다). 없는 API여도 화면 로그아웃은 한다
  const headers = token() ? { Authorization: token() } : {};
  const rt = localStorage.getItem(REFRESH_KEY);
  try { await fetch(API + '/sso/logout', { method: 'POST', headers, body: new URLSearchParams(rt ? { refreshToken: rt } : {}) }); } catch { /* 무시 */ }
  clearTokens();
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
    h('p', { class: 'muted' }, '가짜 계정: alice / bob, 비밀번호 daon-pass-1!'),
    h('p', { class: 'muted' }, h('a', { href: '/signup', 'data-link': true, testid: 'login-to-signup' }, '회원가입'), ' · ',
      h('a', { href: '/reset', 'data-link': true, testid: 'login-to-reset' }, '비밀번호를 잊었어요')));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    try {
      const d = await api('/sso/login', { method: 'POST', form: { username: form.username.value, password: form.password.value } });
      saveTokens(d);
      toast('로그인했습니다');
      go('/');
    } catch (ex) {
      err.textContent = ex.message;
    }
  });
  page('page-login', h('h1', {}, '로그인'), form);
}

// ── 회원가입(원본 /sso/register: 아이디·비밀번호·전화·인증 번호) ──
// 인증 번호는 문자로 온다. 랩의 가짜 문자함: http://127.0.0.1:9102/v1/messages?to=<전화>
async function requestAuthCode(phone, codeInput, note) {
  const d = await api(`/sso/getAuthCode?telephone=${encodeURIComponent(phone)}`);
  if (typeof d === 'string' && /^\d{6}$/.test(d)) {
    codeInput.value = d;   // 서버 응답에 번호가 들어 있으면 그대로 채운다(원본 데모 구현. 4-5에서 문자로만 가게 고친다)
    note.textContent = '서버 응답에 인증 번호가 들어 있어 칸을 채웠습니다.';
  } else {
    note.textContent = `문자를 보냈습니다. 랩의 가짜 문자함(127.0.0.1:9102)에서 ${phone}로 온 번호를 확인하세요.`;
  }
}

function signupPage() {
  const err = h('p', { class: 'error', testid: 'signup-error' }, '');
  const note = h('p', { class: 'muted', testid: 'signup-code-note' }, '');
  const code = h('input', { name: 'authCode', inputmode: 'numeric', required: true, testid: 'signup-code' });
  const form = h('form', { class: 'login', testid: 'signup-form' },
    h('label', {}, '아이디', h('input', { name: 'username', autocomplete: 'username', required: true, testid: 'signup-username' })),
    h('label', {}, '비밀번호', h('input', { name: 'password', type: 'password', autocomplete: 'new-password', required: true, testid: 'signup-password' })),
    h('p', { class: 'muted' }, '긴 문장형 비밀번호를 권합니다. 다른 곳에서 쓰는 비밀번호는 피하세요.'),
    h('label', {}, '전화(가짜 번호 010-0000-xxxx)', h('input', { name: 'telephone', required: true, placeholder: '010-0000-0000', testid: 'signup-phone' })),
    h('button', { type: 'button', class: 'secondary', testid: 'signup-get-code', onclick: async () => {
      err.textContent = '';
      try { await requestAuthCode(form.telephone.value, code, note); } catch (ex) { err.textContent = ex.message; }
    } }, '인증 번호 받기'),
    note,
    h('label', {}, '인증 번호', code),
    err,
    h('button', { type: 'submit', testid: 'signup-submit' }, '가입하기'));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    try {
      await api('/sso/register', { method: 'POST', form: {
        username: form.username.value, password: form.password.value, telephone: form.telephone.value, authCode: code.value } });
      toast('가입했습니다. 로그인해 주세요');
      go('/login');
    } catch (ex) {
      err.textContent = ex.message;
    }
  });
  page('page-signup', h('h1', {}, '회원가입'), form);
}

// ── 비밀번호 재설정: 메일 링크(다온마켓 교육용 추가, 4-5) · 문자 인증 번호(원본 /sso/updatePassword) ──
function resetPage() {
  const tokenParam = new URLSearchParams(location.search).get('token');
  if (tokenParam) {
    const err = h('p', { class: 'error', testid: 'reset-confirm-error' }, '');
    const form = h('form', { class: 'login', testid: 'reset-confirm-form' },
      h('label', {}, '새 비밀번호', h('input', { name: 'password', type: 'password', autocomplete: 'new-password', required: true, testid: 'reset-new-password' })),
      err,
      h('button', { type: 'submit', testid: 'reset-confirm-submit' }, '비밀번호 바꾸기'));
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      err.textContent = '';
      try {
        await api('/sso/reset/confirm', { method: 'POST', form: { token: tokenParam, password: form.password.value } });
        toast('비밀번호를 바꿨습니다. 로그인해 주세요');
        go('/login');
      } catch (ex) {
        err.textContent = ex.message;
      }
    });
    return page('page-reset-confirm', h('h1', {}, '새 비밀번호 정하기'), form);
  }
  const msg = h('p', { class: 'muted', testid: 'reset-link-message' }, '');
  const linkForm = h('form', { class: 'login', testid: 'reset-link-form' },
    h('h2', {}, '메일로 재설정 링크 받기'),
    h('label', {}, '아이디', h('input', { name: 'username', required: true, testid: 'reset-username' })),
    msg,
    h('button', { type: 'submit', testid: 'reset-link-submit' }, '링크 보내기'),
    h('p', { class: 'muted' }, '랩의 가짜 메일함: http://127.0.0.1:8025'));
  linkForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api('/sso/reset/link', { method: 'POST', form: { username: linkForm.username.value } });
      msg.textContent = '가입한 메일로 재설정 링크를 보냈습니다.';
    } catch (ex) {
      msg.textContent = ex.message;
    }
  });
  const err = h('p', { class: 'error', testid: 'reset-phone-error' }, '');
  const note = h('p', { class: 'muted', testid: 'reset-code-note' }, '');
  const code = h('input', { name: 'authCode', inputmode: 'numeric', required: true, testid: 'reset-code' });
  const phoneForm = h('form', { class: 'login', testid: 'reset-phone-form' },
    h('h2', {}, '문자 인증 번호로 바꾸기'),
    h('label', {}, '전화', h('input', { name: 'telephone', required: true, placeholder: '010-0000-0000', testid: 'reset-phone' })),
    h('button', { type: 'button', class: 'secondary', testid: 'reset-get-code', onclick: async () => {
      err.textContent = '';
      try { await requestAuthCode(phoneForm.telephone.value, code, note); } catch (ex) { err.textContent = ex.message; }
    } }, '인증 번호 받기'),
    note,
    h('label', {}, '인증 번호', code),
    h('label', {}, '새 비밀번호', h('input', { name: 'password', type: 'password', autocomplete: 'new-password', required: true, testid: 'reset-phone-password' })),
    err,
    h('button', { type: 'submit', testid: 'reset-phone-submit' }, '비밀번호 바꾸기'));
  phoneForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    err.textContent = '';
    try {
      await api('/sso/updatePassword', { method: 'POST', form: {
        telephone: phoneForm.telephone.value, password: phoneForm.password.value, authCode: code.value } });
      toast('비밀번호를 바꿨습니다. 로그인해 주세요');
      go('/login');
    } catch (ex) {
      err.textContent = ex.message;
    }
  });
  page('page-reset', h('h1', {}, '비밀번호 재설정'), linkForm, phoneForm);
}

// ── 상품 ──
const SORTS = [['new', '신상품'], ['price_asc', '가격 낮은 순'], ['price_desc', '가격 높은 순'], ['sale', '판매량']];

async function productsPage() {
  const q = new URLSearchParams(location.search);
  const keyword = q.get('keyword') || '';
  const sort = q.get('sort') || 'new';
  // 검색어가 있으면 다온마켓 상품 검색(3-2·3-3, 교육용 추가), 없으면 원본 상품 검색
  const list = keyword || q.has('sort')
    ? await api(`/daon/product/search?keyword=${encodeURIComponent(keyword)}&sort=${encodeURIComponent(sort)}`)
    : (await api('/product/search?brandId=101&pageNum=1&pageSize=60&sort=0')).list;
  const cards = list.map((p) => h('article', { class: 'card', testid: `product-card-${p.id}` },
    h('div', { class: 'thumb', 'aria-hidden': 'true' }, p.name.replace(/^다온\s*/, '').slice(0, 1)),
    h('h2', { testid: `product-name-${p.id}` }, p.name),
    h('div', { class: 'muted' }, p.subTitle || ''),
    h('div', { class: 'price', testid: `product-price-${p.id}` }, won(p.price)),
    h('button', { type: 'button', testid: `add-to-cart-${p.id}`, onclick: () => addToCart(p.id) }, '장바구니 담기'),
    h('a', { href: `/reviews/new/${p.id}`, 'data-link': true, class: 'muted', testid: `write-review-${p.id}` }, '후기 쓰기')));
  const form = h('form', { class: 'search', testid: 'search-form' },
    h('input', { name: 'keyword', value: keyword, placeholder: '상품 검색', testid: 'search-keyword' }),
    h('select', { name: 'sort', testid: 'search-sort' }, SORTS.map(([v, label]) => h('option', { value: v, selected: v === sort }, label))),
    h('button', { type: 'submit', testid: 'search-submit' }, '검색'));
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    go(`/?keyword=${encodeURIComponent(form.keyword.value)}&sort=${encodeURIComponent(form.sort.value)}`);
  });
  page('page-products', h('h1', {}, '상품'), form,
    keyword ? h('p', { class: 'muted', testid: 'search-count' }, `「${keyword}」 검색 결과 ${list.length}개`) : null,
    h('div', { class: 'grid', testid: 'product-list' }, cards));
}

// ── 후기 쓰기(3-4, 교육용 추가) ──
async function reviewPage(productId) {
  if (!token()) return loginPage('로그인하면 후기를 쓸 수 있습니다');
  const d = await api(`/product/detail/${productId}`);
  const form = h('form', { class: 'login', testid: 'review-form' },
    h('label', {}, '별점', h('select', { name: 'star', testid: 'review-star' },
      [5, 4, 3, 2, 1].map((n) => h('option', { value: n }, '★'.repeat(n))))),
    h('label', {}, '후기', h('textarea', { name: 'content', rows: 5, maxlength: 500, required: true, testid: 'review-content' })),
    h('button', { type: 'submit', testid: 'review-submit' }, '후기 남기기'));
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      const r = await api('/daon/review', { method: 'POST', json: { productId, star: Number(form.star.value), content: form.content.value } });
      toast(`후기를 남겼습니다(번호 ${r.id})`);
      go('/');
    } catch (ex) {
      toast(ex.message);
    }
  });
  page('page-review', h('h1', {}, `후기 쓰기: ${d.product.name}`), form);
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
