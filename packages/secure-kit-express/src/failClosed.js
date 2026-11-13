// 부품 18 fail-closed · kit.failClosed()  (수업 9-2)
// 본인 확인·권한 확인을 다른 서비스에 묻는 곳에 끼운다(②). 그 서비스가 죽었을 때 「일단 통과」가 되지 않게 한다.
//   router.get('/x', kit.failClosed(async (req) => idService.check(req)), handler)
// check 결과: true → 통과, false → 403, 예외·시간 초과 → 503 + auth.fail_closed.dependency_error
// 풀려면 설정 'fail-closed': { 'fail-open': true } (권하지 않는다. 그 사실도 이벤트로 남긴다)
import { sendError } from './http.js';

const PART = 'fail-closed';

export function createFailClosed({ events, check, opts = {}, failOpen = false, enabled = true }) {
  if (typeof check !== 'function') throw new Error('secure-kit: failClosed(check) 에 확인 함수가 필요합니다');
  const timeoutMs = opts.timeoutMs ?? 3000;
  const dep = opts.dependency ?? 'auth';

  const withTimeout = (p) => new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('시간 초과')), timeoutMs);
    Promise.resolve(p).then((v) => { clearTimeout(t); resolve(v); }, (e) => { clearTimeout(t); reject(e); });
  });

  return async function failClosed(req, res, next) {
    if (!enabled) return next();
    let ok;
    try {
      ok = await withTimeout(check(req));
    } catch {
      if (failOpen) {
        events.emit(req, { event: 'auth.fail_open.dependency_error', part: PART, target: `dep:${dep}`, reason: '의존성 오류인데 fail-open 설정으로 통과' });
        return next();
      }
      events.emit(req, { event: 'auth.fail_closed.dependency_error', part: PART, target: `dep:${dep}`, reason: '의존성 오류로 막음' });
      return sendError(req, res, 503, '잠시 후 다시 시도하세요');
    }
    if (ok === true) return next();
    sendError(req, res, 403, '권한이 없습니다');
  };
}
