// 앱 로그. data/app.log 에 한 줄씩 JSON으로 쓴다.
import { appendFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

export function createLogger(dataDir) {
  mkdirSync(dataDir, { recursive: true });
  const appFile = join(dataDir, 'app.log');
  const auditFile = join(dataDir, 'audit.log');
  return {
    info(msg, data = {}) {
      appendFileSync(appFile, JSON.stringify({ time: new Date().toISOString(), level: 'INFO', msg, ...data }) + '\n');
    },
    // ✏️ 8-3: 누가·언제·무엇을·몇 건 봤는지 감사 기록에 남긴다(값 자체는 남기지 않는다)
    audit(req, action, detail = {}) {
      const actor = `${req.user.role}:${req.user.id}`;
      appendFileSync(auditFile, JSON.stringify({ time: new Date().toISOString(), actor, action, ip: req.ip, ...detail }) + '\n');
    },
  };
}

const SENSITIVE = /pass|token|secret|phone/i;
function mask(value) {
  if (Array.isArray(value)) return value.map(mask);
  if (!value || typeof value !== 'object') return value;
  return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, SENSITIVE.test(k) ? '***' : mask(v)]));
}

export function requestLogger(log) {
  return (req, res, next) => {
    log.info('request', { method: req.method, path: req.path, body: mask(req.body) });
    next();
  };
}
