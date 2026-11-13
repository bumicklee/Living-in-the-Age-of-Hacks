// 오류 처리기. 앱의 맨 끝에 둔다.
import { randomUUID } from 'node:crypto';

// ✏️ 6-5: 자세한 내용은 로그에만 남기고, 응답에는 일반 문구와 추적 번호만 준다
export function errorHandler(log) {
  return (err, req, res, next) => {
    const status = err.status ?? 500;
    const traceId = randomUUID().slice(0, 8);
    log.info('error', { traceId, status, path: req.path, error: err.message, stack: err.stack });
    const message = status < 500 ? '요청을 처리할 수 없습니다' : '잠시 뒤 다시 시도하세요';
    res.status(status).json({ message, traceId });
  };
}
