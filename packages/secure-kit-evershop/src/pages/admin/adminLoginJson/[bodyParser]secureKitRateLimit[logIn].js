// 관리자 로그인(POST /user/login): 고객 로그인과 같은 설정(rate-limit.login)을 쓴다. [뼈대]
import { kit } from '../../../kit.js';

export default kit.rateLimit('login');
