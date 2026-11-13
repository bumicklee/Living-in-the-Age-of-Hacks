// 재연용: 실행 중인 앱(npm start)에 요청을 보낸다.
import { LAB_USERS } from '../app/db.js';

export const BASE_URL = process.env.SHOP_URL ?? `http://127.0.0.1:${process.env.PORT ?? 4300}`;

export async function request(method, path, { token, body, raw } = {}) {
  const headers = {};
  if (token) headers.authorization = `Bearer ${token}`;
  if (body !== undefined || raw !== undefined) headers['content-type'] = 'application/json';
  const res = await fetch(BASE_URL + path, { method, headers, body: raw ?? (body === undefined ? undefined : JSON.stringify(body)) });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* HTML 등 */ }
  return { status: res.status, text, json, headers: res.headers };
}

export async function login(username, password) {
  const user = LAB_USERS.find((u) => u.username === username);
  const res = await request('POST', '/api/login', { body: { username, password: password ?? user.password } });
  return res.json?.token;
}

export async function appIsUp() {
  try {
    const res = await fetch(BASE_URL + '/api/health');
    return res.ok;
  } catch {
    return false;
  }
}
