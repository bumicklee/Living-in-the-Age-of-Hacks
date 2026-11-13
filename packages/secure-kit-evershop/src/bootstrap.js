// EverShop 확장의 bootstrap(기동 때 한 번). kit을 만들면 기동 검사(부품 13)가 돈다. 걸리면 여기서 멈춘다.
// [뼈대] 시험하지 않았다.
import { kit } from './kit.js';

export default () => {
  kit.logger().info('secure-kit-evershop 연결됨(headers·errorResponse·rateLimit login)');
};
