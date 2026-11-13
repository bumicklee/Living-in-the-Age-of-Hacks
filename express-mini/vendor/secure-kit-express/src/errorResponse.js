// 부품 14 error-response · kit.errorResponse()  (수업 6-5)
// 처리 중 예외가 나도 밖으로는 일반 문구와 추적 번호만 나간다. 예외 이름·SQL·스택은 로그(가린 뒤)에만.
//   { "code": 500, "message": "요청을 처리하지 못했습니다", "traceId": "…" }
// Express 오류 처리기(인자 4개)라서 「모든 라우트 뒤」 맨 끝에 둔다:
//   app.use(routes); app.use(kit.notFound()); app.use(kit.errorResponse());
// 설정: 'error-response': { message: '요청을 처리하지 못했습니다' }
import { KitHttpError, sendError, traceId } from './http.js';

const CLIENT_MESSAGES = {
  400: '잘못된 요청입니다',
  401: '로그인이 필요합니다',
  403: '권한이 없습니다',
  404: '찾을 수 없습니다',
  405: '허용하지 않는 방법입니다',
  409: '이미 처리된 요청입니다',
  413: '요청이 너무 큽니다',
  415: '받을 수 없는 형식입니다',
  422: '처리할 수 없는 요청입니다',
  429: '잠시 후 다시 시도하세요'
};

export function createErrorResponse({ logger, cfg = {}, enabled = true }) {
  const generic = cfg.message ?? '요청을 처리하지 못했습니다';

  // eslint-disable-next-line no-unused-vars
  return function errorResponse(err, req, res, next) {
    if (res.headersSent) return next(err); // 이미 보내기 시작했으면 Express 기본 처리(연결 닫기)
    const tid = traceId(req, res);
    if (!enabled) return next(err); // 영상 시연용: 고치기 전 모습

    // 부품이 일부러 던진 오류는 그 상태·문구 그대로(이미 일반 문구다)
    if (err instanceof KitHttpError) {
      return sendError(req, res, err.status, err.message, err.body ?? {}, err.headers ?? {});
    }
    const status = Number(err?.status ?? err?.statusCode);
    // body-parser 같은 미들웨어가 붙인 4xx(expose) 는 상태만 살리고 문구는 우리 것
    if (status >= 400 && status < 500 && err?.expose !== false) {
      return sendError(req, res, status, CLIENT_MESSAGES[status] ?? CLIENT_MESSAGES[400]);
    }
    // 내부 오류: 자세한 내용은 로그에만(로거가 가린다). 같은 추적 번호로 찾는다
    logger.error(`처리 중 오류 trace_id=${tid} ${req.method} ${req.path ?? req.url}`, err);
    sendError(req, res, 500, generic);
  };
}

/** 라우트가 없을 때도 같은 모양(Express 기본 HTML 404 대신) */
export function createNotFound() {
  return function notFound(req, res) {
    sendError(req, res, 404, CLIENT_MESSAGES[404]);
  };
}
