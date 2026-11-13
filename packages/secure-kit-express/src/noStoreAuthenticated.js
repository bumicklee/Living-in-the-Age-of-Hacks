// 부품 22 no-store · kit.noStoreAuthenticated()  (수업 11-4)
// 로그인한 요청(Authorization 머리가 있는 요청)의 응답은 CDN·프록시·브라우저가 저장하지 않게 한다(①).
// 처리 함수가 나중에 Cache-Control: public 을 넣어도 인증 요청이면 no-store 로 되돌린다.
// 설정: 'cache-control': { authenticated: 'no-store', 'include-cookie': false }
//   include-cookie: true 면 쿠키 세션 요청에도 적용(쿠키를 쓰는 서비스라면 켠다)

export function createNoStoreAuthenticated({ cfg = {}, enabled = true }) {
  const value = cfg.authenticated ?? 'no-store';
  const includeCookie = cfg['include-cookie'] === true;

  return function noStoreAuthenticated(req, res, next) {
    if (!enabled) return next();
    const authed = Boolean(req.headers.authorization) || (includeCookie && Boolean(req.headers.cookie));
    if (!authed) return next();
    res.setHeader('Cache-Control', value);
    res.setHeader('Pragma', 'no-cache');
    // 처리 함수가 덮어쓰려 해도 막는다
    const setHeader = res.setHeader.bind(res);
    res.setHeader = (name, v) => (String(name).toLowerCase() === 'cache-control' ? res : setHeader(name, v));
    next();
  };
}
