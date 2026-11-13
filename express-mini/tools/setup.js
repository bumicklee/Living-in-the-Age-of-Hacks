// npm install 뒤 자동으로 한 번: 설정 파일과 DB를 만든다.
import { existsSync } from 'node:fs';
import { ENV, writeEnv } from './files.js';

if (!existsSync(ENV)) {
  // 처음에는 비밀 값이 비어 있다(6-3 수업에서 채운다)
  writeEnv({ PORT: '4300', SHOP_SECRET: '' });
  console.log('설정 파일 .env 를 만들었습니다.');
}
console.log('준비 끝. 다음: npm start');
