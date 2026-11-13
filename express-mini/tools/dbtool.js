// 실행 중인 앱과 같은 DB 파일을 직접 열어 되돌린다.
import { openDb, seed } from '../app/db.js';
import { DATA } from './files.js';
import { rmSync } from 'node:fs';
import { join } from 'node:path';

export function resetData() {
  const db = openDb(DATA);
  seed(db);
  db.close();
  for (const f of ['app.log', 'audit.log']) rmSync(join(DATA, f), { force: true });
}

export function withDb(fn) {
  const db = openDb(DATA);
  try {
    return fn(db);
  } finally {
    db.close();
  }
}
