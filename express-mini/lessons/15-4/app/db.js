// 가게 DB(SQLite). 파일은 data/shop.db 하나다. 값은 모두 가짜다.
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { hashPassword } from './lib/password.js';

const TABLES = `
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL,
  name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, role TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS orders (
  id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, item TEXT NOT NULL,
  amount INTEGER NOT NULL, address TEXT NOT NULL, status TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS products (
  id INTEGER PRIMARY KEY, name TEXT NOT NULL, price INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS reviews (
  id INTEGER PRIMARY KEY, product_id INTEGER NOT NULL, user_id INTEGER NOT NULL, body TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS coupons (code TEXT PRIMARY KEY, remaining INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS coupon_uses (id INTEGER PRIMARY KEY, code TEXT NOT NULL, user_id INTEGER NOT NULL);
`;

// 랩 계정. 비밀번호는 실습용 가짜 값이다.
export const LAB_USERS = [
  { id: 1, username: 'alice', password: 'alice-1234', name: '앨리스', phone: '010-0000-1001', address: '서울시 가짜구 연습로 1', role: 'customer' },
  { id: 2, username: 'bob', password: 'bob-1234', name: '밥', phone: '010-0000-1002', address: '부산시 가짜구 연습로 2', role: 'customer' },
  { id: 3, username: 'carol', password: 'carol-1234', name: '캐럴', phone: '010-0000-1003', address: '대구시 가짜구 연습로 3', role: 'customer' },
  { id: 4, username: 'staff', password: 'staff-1234', name: '직원', phone: '010-0000-2001', address: '다온마켓 본사', role: 'staff' },
  { id: 5, username: 'admin', password: 'admin-1234', name: '관리자', phone: '010-0000-2002', address: '다온마켓 본사', role: 'admin' },
];

export function openDb(dataDir) {
  let file = ':memory:';
  if (dataDir !== ':memory:') {
    mkdirSync(dataDir, { recursive: true });
    file = join(dataDir, 'shop.db');
  }
  const db = new DatabaseSync(file);
  if (file !== ':memory:') db.exec('PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 2000;');
  db.exec(TABLES);
  if (db.prepare('SELECT count(*) AS n FROM users').get().n === 0) seed(db);
  return db;
}

// 모든 표를 처음 상태로 되돌린다.
export function seed(db) {
  db.exec('DELETE FROM coupon_uses; DELETE FROM coupons; DELETE FROM reviews; DELETE FROM products; DELETE FROM orders; DELETE FROM users;');
  const addUser = db.prepare('INSERT INTO users (id, username, password_hash, name, phone, address, role) VALUES (?, ?, ?, ?, ?, ?, ?)');
  for (const u of LAB_USERS) addUser.run(u.id, u.username, hashPassword(u.password), u.name, u.phone, u.address, u.role);
  const addOrder = db.prepare('INSERT INTO orders (id, user_id, item, amount, address, status) VALUES (?, ?, ?, ?, ?, ?)');
  addOrder.run(1001, 1, '무선 이어폰', 89000, '서울시 가짜구 연습로 1', '배송 중');
  addOrder.run(1002, 1, '보조 배터리', 32000, '서울시 가짜구 연습로 1', '결제 완료');
  addOrder.run(2001, 2, '키보드', 54000, '부산시 가짜구 연습로 2', '결제 완료');
  const addProduct = db.prepare('INSERT INTO products (id, name, price, hidden) VALUES (?, ?, ?, ?)');
  addProduct.run(1, '무선 이어폰', 89000, 0);
  addProduct.run(2, '보조 배터리', 32000, 0);
  addProduct.run(3, '키보드', 54000, 0);
  addProduct.run(4, '출시 전 스마트워치(비공개)', 199000, 1);
  addProduct.run(5, '직원 할인 상품(비공개)', 1000, 1);
  db.prepare('INSERT INTO reviews (product_id, user_id, body) VALUES (?, ?, ?)').run(1, 2, '소리가 깨끗해요.');
  db.prepare('INSERT INTO coupons (code, remaining) VALUES (?, ?)').run('WELCOME', 1);
}
