// 화면(페이지) 요청 전체에 보안 헤더·CORS·X-Trace-Id. 파일 이름 [context] = context 미들웨어 다음에 돈다(EverShop 규칙).
// [뼈대] EverShop은 자체 CSP 설정이 있을 수 있다. 화면이 깨지면 secure-kit.headers.csp 를 조정한다.
import { kit } from '../../kit.js';

export default kit.headers();
