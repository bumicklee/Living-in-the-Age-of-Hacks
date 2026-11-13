// 새 비밀 값을 만들어 .env 에 넣는다: npm run secret
import { randomBytes } from 'node:crypto';
import { readEnv, writeEnv } from './files.js';

const env = readEnv();
env.SHOP_SECRET = randomBytes(32).toString('base64url');
writeEnv(env);
console.log('새 비밀 값을 .env 의 SHOP_SECRET 에 넣었습니다. npm start 로 켜 둔 앱은 스스로 다시 시작합니다.');
