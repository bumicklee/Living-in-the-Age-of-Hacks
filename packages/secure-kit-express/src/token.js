// 부품 11 token · kit.token()  (수업 4-8·5-2)
// 짧은 수명 JWT(HS256) 발급·확인·폐기. 설정만 바꿔 쓴다(①).
//   token: { secret, kid: 'k1', audience: 'kit', ttl: '15m', 'partner-audience': 'partner' }
// 확인 순서: 모양 → 머리 alg가 정확히 HS256(none·RS256 바꿔치기 거부) → kid → 서명(timingSafeEqual) → exp 필수·만료 → aud → 폐기 목록.
// 폐기는 jti를 저장소에 exp까지 둔다(로그아웃·탈취 신고).
import { createHmac, randomUUID, timingSafeEqual } from 'node:crypto';
import { parseDuration } from './config.js';
import { sendError } from './http.js';

const PART = 'token';
const MAX_TTL_S = 900;

const b64url = (buf) => Buffer.from(buf).toString('base64url');
const fromB64Json = (s) => JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));

export class TokenError extends Error {
  constructor(event, reason) {
    super(reason);
    this.event = event;
  }
}

export function createToken({ events, store, cfg = {} }) {
  const secret = cfg.secret;
  if (!secret || String(secret).length < 32) throw new Error('secure-kit: token.secret은 32자 이상이어야 합니다');
  const kid = cfg.kid ?? 'k1';
  const audience = cfg.audience ?? 'kit';
  const partnerAudience = cfg['partner-audience'] ?? 'partner';
  const ttlS = Math.min(MAX_TTL_S, Math.floor(parseDuration(cfg.ttl, MAX_TTL_S * 1000) / 1000));
  const sign = (data) => createHmac('sha256', secret).update(data).digest();

  function encode(payload) {
    const head = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT', kid }));
    const body = b64url(JSON.stringify(payload));
    return `${head}.${body}.${b64url(sign(`${head}.${body}`))}`;
  }

  /** 사람 토큰. user = { sub, roles } */
  function issue(user) {
    const now = Math.floor(Date.now() / 1000);
    const token = encode({ sub: user.sub, aud: audience, iat: now, auth_time: user.authTime ?? now, exp: now + ttlS, jti: randomUUID(), roles: user.roles ?? [] });
    return { token, expiresIn: ttlS };
  }

  /** 파트너 토큰(aud partner, sub = 앱, scope = 공백 구분) */
  function issuePartner(app, scopes = []) {
    const now = Math.floor(Date.now() / 1000);
    return { token: encode({ sub: app, aud: partnerAudience, iat: now, exp: now + ttlS, jti: randomUUID(), scope: scopes.join(' ') }), expiresIn: ttlS };
  }

  /** 맞으면 내용(claims), 아니면 TokenError */
  async function verify(token, { aud = audience } = {}) {
    const parts = String(token ?? '').split('.');
    if (parts.length !== 3) throw new TokenError('auth.token.invalid', '모양이 틀림');
    let head;
    let body;
    try {
      head = fromB64Json(parts[0]);
      body = fromB64Json(parts[1]);
    } catch {
      throw new TokenError('auth.token.invalid', '읽을 수 없음');
    }
    if (head.alg !== 'HS256') throw new TokenError('auth.token.denied_alg', '허용하지 않은 alg');
    if (head.kid !== kid) throw new TokenError('auth.token.invalid', '모르는 kid');
    const want = sign(`${parts[0]}.${parts[1]}`);
    const got = Buffer.from(parts[2], 'base64url');
    if (got.length !== want.length || !timingSafeEqual(got, want)) throw new TokenError('auth.token.bad_signature', '서명 불일치');
    const now = Math.floor(Date.now() / 1000);
    if (typeof body.exp !== 'number') throw new TokenError('auth.token.invalid', 'exp 없음');
    if (body.exp <= now) throw new TokenError('auth.token.expired', '만료');
    if (body.aud !== aud) throw new TokenError('auth.token.denied_aud', '대상(aud)이 다름');
    if (body.jti && (await store.get(`revoked:${body.jti}`))) {
      throw Object.assign(new TokenError('auth.token.revoked_used', '폐기한 토큰 사용'), { jti: body.jti });
    }
    return body;
  }

  async function revoke(claims) {
    const left = Math.max(1, claims.exp - Math.floor(Date.now() / 1000));
    await store.set(`revoked:${claims.jti}`, 1, left * 1000);
  }

  /**
   * 미들웨어. Bearer 토큰을 확인해 req.secureKitUser(사람) 또는 req.secureKitPartner(파트너)를 넣는다.
   * @param opts.required  false면 토큰이 없을 때 그냥 통과(있는데 틀리면 401)
   * @param opts.aud       기본은 사람 토큰 대상
   */
  function authenticate({ required = true, aud = audience } = {}) {
    return async function authenticateToken(req, res, next) {
      const h = req.headers.authorization;
      if (typeof h !== 'string' || !h.startsWith('Bearer ')) {
        return required ? sendError(req, res, 401, '로그인이 필요합니다') : next();
      }
      try {
        const claims = await verify(h.slice(7).trim(), { aud });
        if (aud === partnerAudience) req.secureKitPartner = { app: claims.sub, scopes: String(claims.scope ?? '').split(' ').filter(Boolean), claims };
        else req.secureKitUser = { sub: claims.sub, roles: claims.roles ?? [], claims };
        next();
      } catch (err) {
        if (!(err instanceof TokenError)) return next(err);
        const target = err.jti ? `jti:${err.jti.slice(0, 8)}` : 'token:bearer';
        events.emit(req, { event: err.event, part: PART, target, reason: err.message });
        sendError(req, res, 401, '로그인이 필요합니다');
      }
    };
  }

  return { issue, issuePartner, verify, revoke, authenticate, audience, partnerAudience, ttlSeconds: ttlS };
}
