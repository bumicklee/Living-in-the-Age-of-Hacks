// 앱 설정. 값은 .env 파일(환경 변수)에서 읽는다.
import { fileURLToPath } from 'node:url';

const DATA_DIR = fileURLToPath(new URL('../data/', import.meta.url));

// ✏️ 6-3: 널리 알려진 값이나 짧은 값은 비밀이 아니다
const KNOWN_WEAK = ['keyboard cat', 'secret', 'changeme', 'password'];

export function loadConfig(env = process.env) {
  const secret = env.SHOP_SECRET ?? '';
  if (secret.length < 32 || KNOWN_WEAK.includes(secret)) {
    throw new Error('SHOP_SECRET이 없거나 너무 짧습니다. npm run secret 으로 새 값을 만든 뒤 다시 시작하세요');
  }
  return {
    port: Number(env.PORT ?? 4300),
    dataDir: env.SHOP_DATA ?? DATA_DIR,
    // 상품 이미지를 가져와도 되는 서버(쉼표로 구분)
    imageHosts: (env.SHOP_IMAGE_HOSTS ?? 'images.daon.example').split(',').map((h) => h.trim()).filter(Boolean),
    secret,
  };
}
