// 고객 로그인(POST /customer/login): 본문을 읽은 뒤, 로그인 처리 전에 속도 제한. [뼈대]
import { kit } from '../../../kit.js';

export default kit.rateLimit('login');
