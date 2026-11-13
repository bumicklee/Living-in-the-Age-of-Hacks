// 부품 9 reset-link · kit.resetLink()  (수업 4-5)
// 비밀번호 재설정 링크를 만들고(issue) 쓰게 한다(consume). 직접 링크를 조립하는 대신 이것으로 바꾼다(③).
// - 링크 앞부분은 설정 'reset-link.base-url' 하나뿐이다. 요청의 Host·X-Forwarded-Host로 정하지 않는다.
// - 토큰은 무작위 32바이트, 저장소에는 해시만, 15분, 한 번만.
// 설정: 'reset-link': { 'base-url': 'http://shop.example', path: '/reset', ttl: '15m' }
import { createHash, randomBytes } from 'node:crypto';
import { safeTarget } from './events.js';
import { KitHttpError } from './http.js';
import { hostWithoutPort } from './hostAllowlist.js';
import { parseDuration } from './config.js';

const PART = 'reset-link';
const sha = (t) => createHash('sha256').update(t).digest('hex');

export function createResetLink({ events, store, cfg = {}, send }) {
  const base = cfg['base-url'];
  if (!base) throw new Error('secure-kit: reset-link.base-url 설정이 필요합니다');
  const baseHost = new URL(base).hostname.toLowerCase();
  const path = cfg.path ?? '/reset';
  const ttlMs = parseDuration(cfg.ttl, 15 * 60_000);

  /**
   * 재설정 요청. 사용자가 없어도 같은 시간·같은 응답이 되도록 호출하는 쪽은 결과와 상관없이 같은 문구를 보낸다.
   * @param user  { id, email } | null
   */
  async function issue(req, user) {
    const fwd = req.headers['x-forwarded-host'];
    if (typeof fwd === 'string' && hostWithoutPort(fwd.split(',')[0]) !== baseHost) {
      events.emit(req, { event: 'auth.reset.host_mismatch', part: PART, target: safeTarget('host', fwd), reason: 'X-Forwarded-Host가 base-url과 다름(무시함)' });
    }
    const token = randomBytes(32).toString('hex');
    if (!user) return null;
    await store.set(`reset:${sha(token)}`, { userId: user.id, state: 'issued' }, ttlMs);
    const link = `${base.replace(/\/$/, '')}${path}?token=${token}`;
    await send({
      to: user.email,
      subject: '비밀번호 재설정 안내',
      body: `아래 링크로 비밀번호를 바꾸세요.\n${link}\n링크는 ${Math.round(ttlMs / 60_000)}분 동안 한 번만 쓸 수 있습니다.`
    });
    return link;
  }

  /** 토큰을 쓴다. 성공하면 userId. 다시 쓰거나 없는 토큰이면 KitHttpError(400) */
  async function consume(req, token) {
    const key = `reset:${sha(String(token ?? ''))}`;
    const rec = await store.get(key);
    if (!rec) {
      events.emit(req, { event: 'auth.reset.token_invalid', part: PART, reason: '없거나 만료된 토큰' });
      throw new KitHttpError(400, '링크가 올바르지 않거나 만료되었습니다');
    }
    if (rec.state === 'used') {
      events.emit(req, { event: 'auth.reset.token_reused', part: PART, target: `user:${rec.userId}`, reason: '이미 쓴 토큰' });
      throw new KitHttpError(400, '이미 사용한 링크입니다');
    }
    // 한 번만: 쓴 표시를 남겨 둔다(다시 쓰면 token_reused 로 알 수 있게, 원래 만료까지)
    await store.set(key, { ...rec, state: 'used' }, ttlMs);
    return rec.userId;
  }

  return { issue, consume };
}
