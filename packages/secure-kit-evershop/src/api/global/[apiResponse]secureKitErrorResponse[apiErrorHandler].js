// API 오류 응답을 { code, message, traceId } 로. EverShop 기본 apiErrorHandler 보다 앞에 두려는 이름이다.
// [뼈대] EverShop이 인자 4개 미들웨어를 이 위치에서 오류 처리기로 부르는지 확인하지 않았다.
//        안 되면 diffs/s6-05/express.diff 처럼 apiErrorHandler 본문을 고치는 쪽이 확실하다.
import { kit } from '../../kit.js';

export default kit.errorResponse();
