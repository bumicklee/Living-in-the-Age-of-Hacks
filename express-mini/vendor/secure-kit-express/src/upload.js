// 부품 4 upload · kit.upload()  (수업 3-6)
// 업로드 받는 곳에서 직접 파일을 다루는 대신 이 미들웨어를 앞에 둔다(③).
// - 크기: 'upload.max-size'(기본 2mb) 초과 → 413 upload.rejected.size
// - 종류: 확장자·Content-Type이 아니라 「내용 앞 바이트」로 PNG·JPEG·GIF·WebP만 → 아니면 415 upload.rejected.type
// - 이름: 원래 이름은 쓰지 않는다. 무작위 32자 16진수 + 내용에서 정한 확장자
// 통과하면 req.upload = { stored, type, ext, bytes, buffer }. 저장(디스크·S3)은 앱이 stored 이름으로 한다.
import { randomBytes } from 'node:crypto';
import { safeTarget } from './events.js';
import { readBody, sendError, wrapAsync } from './http.js';
import { parseSize } from './config.js';

const PART = 'upload';

const SIGNATURES = [
  { type: 'image/png', ext: 'png', test: (b) => b.length >= 8 && b.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) },
  { type: 'image/jpeg', ext: 'jpg', test: (b) => b.length >= 3 && b[0] === 0xff && b[1] === 0xd8 && b[2] === 0xff },
  { type: 'image/gif', ext: 'gif', test: (b) => b.length >= 6 && /^GIF8[79]a$/.test(b.subarray(0, 6).toString('latin1')) },
  { type: 'image/webp', ext: 'webp', test: (b) => b.length >= 12 && b.subarray(0, 4).toString('latin1') === 'RIFF' && b.subarray(8, 12).toString('latin1') === 'WEBP' }
];

/** 내용 앞 바이트로 종류를 정한다. 모르면 null */
export function sniffImage(buffer) {
  return SIGNATURES.find((s) => s.test(buffer)) ?? null;
}

export function createUpload({ events, cfg = {} }) {
  const maxBytes = parseSize(cfg['max-size'], 2 * 1024 * 1024);
  const allowed = new Set(cfg.types ?? SIGNATURES.map((s) => s.type));

  return wrapAsync(async function upload(req, res, next) {
    const name = String(req.headers['x-file-name'] ?? '');
    // bodyLimit()이 먼저 읽었으면 req.rawBody를 쓰고, 아니면 상한까지만 읽는다
    const { buffer, tooLarge } = await readBody(req, maxBytes);
    if (tooLarge || (buffer && buffer.length > maxBytes)) {
      events.emit(req, { event: 'upload.rejected.size', part: PART, target: safeTarget('file', name.split(/[\\/]/).pop()), reason: '크기 상한 초과' });
      return sendError(req, res, 413, '파일이 너무 큽니다');
    }
    const kind = sniffImage(buffer ?? Buffer.alloc(0));
    if (!kind || !allowed.has(kind.type)) {
      events.emit(req, { event: 'upload.rejected.type', part: PART, target: safeTarget('file', name.split(/[\\/]/).pop()), reason: '허용하지 않은 파일 내용' });
      return sendError(req, res, 415, '받을 수 없는 파일 형식입니다');
    }
    req.upload = { stored: `${randomBytes(16).toString('hex')}.${kind.ext}`, type: kind.type, ext: kind.ext, bytes: buffer.length, buffer };
    next();
  });
}
