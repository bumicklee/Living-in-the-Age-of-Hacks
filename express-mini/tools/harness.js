// 수업 시험용 작은 실행 환경: 앱을 임시 DB·임시 로그 폴더로 띄운다(npm start 와 별개).
import { mkdtempSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { LAB_USERS } from '../app/db.js';

export const STRONG_SECRET = randomBytes(32).toString('base64url');

export async function createHarness({ env = {} } = {}) {
  const { loadConfig } = await import('../app/config.js');
  const { openDb } = await import('../app/db.js');
  const { createApp } = await import('../app/app.js');
  const { startLabWorld } = await import('../lab-world/index.js');
  const { clock } = await import('../app/lib/clock.js');

  const dataDir = mkdtempSync(join(tmpdir(), 'daon-mini-'));
  const config = loadConfig({ SHOP_SECRET: STRONG_SECRET, ...env, SHOP_DATA: dataDir });
  const world = await startLabWorld();
  const db = openDb(':memory:');
  const app = createApp({ config, db, labNet: world.net });
  const server = await new Promise((resolve) => { const s = app.listen(0, '127.0.0.1', () => resolve(s)); });
  const base = `http://127.0.0.1:${server.address().port}`;
  const realNow = clock.now;

  async function request(method, path, { token, body, raw } = {}) {
    const headers = {};
    if (token) headers.authorization = `Bearer ${token}`;
    if (body !== undefined || raw !== undefined) headers['content-type'] = 'application/json';
    const res = await fetch(base + path, { method, headers, body: raw ?? (body === undefined ? undefined : JSON.stringify(body)) });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* HTML 등 */ }
    return { status: res.status, text, json, headers: res.headers };
  }

  return {
    base, db, config, world, dataDir, clock, request,
    async login(username, password) {
      const user = LAB_USERS.find((u) => u.username === username);
      const res = await request('POST', '/api/login', { body: { username, password: password ?? user.password } });
      return res.json?.token;
    },
    readLog(name) {
      const file = join(dataDir, name);
      return existsSync(file) ? readFileSync(file, 'utf8') : '';
    },
    async close() {
      clock.now = realNow;
      await new Promise((r) => server.close(r));
      world.close();
      db.close();
      rmSync(dataDir, { recursive: true, force: true });
    },
  };
}
