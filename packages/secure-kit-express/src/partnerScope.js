// 부품 23 partner-scope · kit.partnerScope('orders:read')  (수업 12-2·12-3)
// 파트너(택배사·제휴사) API에 한 줄로 끼운다(②).
// - 파트너 토큰(aud partner)만 받는다. 사람 토큰(aud kit)을 들고 오면 401
// - 토큰의 scope(공백 구분)에 필요한 범위가 없으면 403 + partner.denied.scope
// - 바인딩(선택): opts.bind = (req, partner) => boolean  예) 이 택배사에 배정된 주문인지. 아니면 403 partner.denied.binding
import { safeTarget } from './events.js';
import { sendError } from './http.js';

const PART = 'partner-scope';

export function createPartnerScope({ events, token, scope, opts = {}, enabled = true }) {
  const authenticate = token.authenticate({ aud: token.partnerAudience });

  return function partnerScope(req, res, next) {
    if (!enabled) return next();
    const check = () => {
      const p = req.secureKitPartner;
      if (!p?.app) return sendError(req, res, 401, '로그인이 필요합니다');
      if (!p.scopes.includes(scope)) {
        events.emit(req, { event: 'partner.denied.scope', part: PART, target: safeTarget('scope', scope), reason: '토큰 범위 밖' });
        return sendError(req, res, 403, '권한이 없습니다');
      }
      if (opts.bind && opts.bind(req, p) !== true) {
        events.emit(req, { event: 'partner.denied.binding', part: PART, target: safeTarget('scope', scope), reason: '이 파트너에 묶이지 않은 자원' });
        return sendError(req, res, 403, '권한이 없습니다');
      }
      next();
    };
    // 앞에서 이미 파트너 인증을 했으면 그대로 쓰고, 아니면 여기서 한다
    if (req.secureKitPartner) return check();
    authenticate(req, res, (err) => (err ? next(err) : check()));
  };
}
