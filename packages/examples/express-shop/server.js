// express-shop 기동(15-4 전후 같은 파일). 컨테이너 안에서 0.0.0.0 에 열고, 호스트에는 127.0.0.1:18184 로만 내보낸다.
import { createApp } from './app.js';

const port = Number(process.env.PORT || 18184);
createApp().listen(port, '0.0.0.0', () => {
  process.stdout.write(`express-shop 기동 port=${port}\n`);
});
