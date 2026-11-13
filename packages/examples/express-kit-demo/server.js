// express-kit-demo 기동. 컨테이너 안에서 돌기 때문에 0.0.0.0 에 연다.
// 바깥(호스트)에는 compose가 127.0.0.1:18182 로만 내보낸다(공용 망에 열지 않는다).
import { createApp } from './app.js';

const port = Number(process.env.PORT || 18182);
const app = createApp({ port });

app.listen(port, '0.0.0.0', () => {
  app.locals.kit.logger().info(`express-kit-demo 기동 port=${port}`);
});
