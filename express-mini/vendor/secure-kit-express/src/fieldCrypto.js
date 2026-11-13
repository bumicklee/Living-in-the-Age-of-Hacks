// 부품 12 field-crypto · kit.fieldCrypto()  (수업 5-3·5-4)
// 전화번호 같은 개인정보 칸을 저장 직전에 암호화하고, 꺼낼 때 푼다. 검색은 평문 대신 「검색용 해시 칸」으로 한다.
//   저장 값: enc:1:<논스 base64url>:<암호문+태그 base64url>   (AES-256-GCM, 같은 번호도 매번 다른 값)
//   검색 칸: HMAC-SHA256(검색 키, 숫자만 남긴 번호)
// ② 한 줄: const row = kit.fieldCrypto().seal({ phone }, ['phone'])  → { phone: 'enc:1:…', phone_hash: '…' }
// 설정: 'field-crypto': { key: '<32자 이상>', 'index-key': '<32자 이상>' }  ← 실제 서비스는 KMS·OpenBao에서 받는다
// 키 회전: 앞의 「1」이 키 번호다. 새 키를 2번으로 넣고 1번은 풀기 전용으로 남기는 식으로 늘린다.
import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes } from 'node:crypto';
import { maskPhone } from './logger.js';

const PART = 'field-crypto';
const VERSION = '1';

const derive = (secret, label) => createHash('sha256').update(`secure-kit:${label}:${secret}`).digest();

export function createFieldCrypto({ events, cfg = {} }) {
  if (!cfg.key || !cfg['index-key']) throw new Error('secure-kit: field-crypto.key·index-key 설정이 필요합니다');
  const encKey = derive(cfg.key, 'enc');
  const idxKey = derive(cfg['index-key'], 'index');

  function encrypt(plain) {
    if (plain == null) return plain;
    const iv = randomBytes(12);
    const c = createCipheriv('aes-256-gcm', encKey, iv);
    const ct = Buffer.concat([c.update(String(plain), 'utf8'), c.final(), c.getAuthTag()]);
    return `enc:${VERSION}:${iv.toString('base64url')}:${ct.toString('base64url')}`;
  }

  function decrypt(value, req) {
    if (value == null) return value;
    try {
      const [tag, ver, ivB64, ctB64] = String(value).split(':');
      if (tag !== 'enc' || ver !== VERSION) throw new Error('모양');
      const buf = Buffer.from(ctB64, 'base64url');
      const d = createDecipheriv('aes-256-gcm', encKey, Buffer.from(ivB64, 'base64url'));
      d.setAuthTag(buf.subarray(buf.length - 16));
      return Buffer.concat([d.update(buf.subarray(0, buf.length - 16)), d.final()]).toString('utf8');
    } catch {
      // 값은 남기지 않는다. 키가 바뀌었거나 저장 값이 망가졌다
      events.emit(req, { event: 'crypto.decrypt.failed', part: PART, reason: '복호화 실패' });
      throw new Error('secure-kit: 복호화 실패');
    }
  }

  /** 검색용 해시. 전화번호는 숫자만 남겨서(하이픈 있든 없든 같은 값) */
  function blindIndex(plain, { digitsOnly = true } = {}) {
    const norm = digitsOnly ? String(plain).replace(/\D/g, '') : String(plain).trim().toLowerCase();
    return createHmac('sha256', idxKey).update(norm).digest('hex');
  }

  /** 저장 직전: 고른 칸을 암호화하고 <칸>_hash 를 붙인다 */
  function seal(record, fields) {
    const out = { ...record };
    for (const f of fields) {
      if (out[f] == null) continue;
      out[`${f}_hash`] = blindIndex(out[f]);
      out[f] = encrypt(out[f]);
    }
    return out;
  }

  /** 꺼낸 뒤: 고른 칸을 푼다. mask: true 면 화면용으로 가린 값 */
  function open(record, fields, { mask = false, req } = {}) {
    const out = { ...record };
    for (const f of fields) {
      delete out[`${f}_hash`];
      if (out[f] == null) continue;
      const plain = decrypt(out[f], req);
      out[f] = mask ? maskPhone(plain) : plain;
    }
    return out;
  }

  return { encrypt, decrypt, blindIndex, seal, open, mask: maskPhone };
}
