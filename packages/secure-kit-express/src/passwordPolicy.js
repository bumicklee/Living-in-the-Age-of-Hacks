// 부품 7 password-policy · kit.passwordPolicy()  (수업 4-2)
// 가입·비밀번호 변경에서 직접 규칙을 쓰는 대신 이것으로 확인한다(③).
// 규칙은 일부러 단순하다: 길이(기본 10자 이상) + 유출 목록에 없을 것. 복잡도 규칙(특수문자 섞기)은 두지 않는다.
// 설정: 'password-policy': { 'min-length': 10, 'max-length': 128, 'breached-list': [...] }
//   실제 서비스는 유출 목록을 파일·HIBP k-익명 조회로 바꾼다('breached-check' 함수 옵션).

const PART = 'password-policy';

export function createPasswordPolicy({ events, cfg = {}, breachedCheck }) {
  const min = cfg['min-length'] ?? 10;
  const max = cfg['max-length'] ?? 128;
  const breached = new Set((cfg['breached-list'] ?? []).map((p) => String(p).toLowerCase()));
  const isBreached = breachedCheck ?? (async (pw) => breached.has(pw.toLowerCase()));

  /**
   * @returns {Promise<{ok: true} | {ok: false, reason: 'too_short'|'too_long'|'breached'}>}
   */
  async function check(password, req) {
    const pw = typeof password === 'string' ? password : '';
    if ([...pw].length < min) return { ok: false, reason: 'too_short' };
    if ([...pw].length > max) return { ok: false, reason: 'too_long' };
    if (await isBreached(pw)) {
      // 비밀번호 값은 남기지 않는다
      events.emit(req, { event: 'auth.password.rejected_breached', part: PART, level: 'INFO', target: 'password:breached-list', reason: '유출 목록에 있는 비밀번호' });
      return { ok: false, reason: 'breached' };
    }
    return { ok: true };
  }

  return { check };
}
