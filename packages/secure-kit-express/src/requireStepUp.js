// 부품 10 step-up · kit.requireStepUp()  (수업 4-7)
// 이메일·비밀번호·출금 계좌 바꾸기처럼 위험한 작업 앞에 한 줄로 끼운다(②). 로그인 토큰만으로는 부족하고 비밀번호를 한 번 더 받는다.
//   router.post('/account/email', auth, kit.requireStepUp({ verify }), changeEmail)
// verify: async (req, password) => boolean   (보통 constantTimeLogin().verifyPassword 로 현재 사용자 해시와 비교)
// 머리 이름: 'step-up.header'(기본 X-Reauth-Password). 실제 서비스는 OTP·패스키로 바꿔 끼운다.
import { sendError } from './http.js';

const PART = 'step-up';

export function createRequireStepUp({ events, cfg = {}, verify, action = 'account.change', enabled = true }) {
  if (typeof verify !== 'function') throw new Error('secure-kit: requireStepUp({ verify }) 가 필요합니다');
  const header = (cfg.header ?? 'X-Reauth-Password').toLowerCase();

  return async function requireStepUp(req, res, next) {
    if (!enabled) return next();
    const sub = req.secureKitUser?.sub;
    if (!sub) return sendError(req, res, 401, '로그인이 필요합니다');
    const given = req.headers[header];
    if (typeof given !== 'string' || given.length === 0) {
      events.emit(req, { event: 'auth.stepup.required', part: PART, level: 'INFO', target: `user:${sub}`, reason: '재인증 없음' });
      return sendError(req, res, 401, '다시 인증해 주세요', { stepUp: 'required' });
    }
    let ok = false;
    try {
      ok = await verify(req, given);
    } catch {
      ok = false; // 확인하다 실패하면 통과시키지 않는다
    }
    if (!ok) {
      events.emit(req, { event: 'auth.stepup.failed', part: PART, target: `user:${sub}`, reason: '재인증 실패' });
      return sendError(req, res, 401, '다시 인증해 주세요', { stepUp: 'failed' });
    }
    events.emit(req, { event: `${action}.high_risk`, part: PART, level: 'INFO', target: `user:${sub}`, reason: '재인증 뒤 고위험 변경' });
    next();
  };
}
