// 앱 설정. 값은 .env 파일(환경 변수)에서 읽는다.
import { fileURLToPath } from 'node:url';

const DATA_DIR = fileURLToPath(new URL('../data/', import.meta.url));

export function loadConfig(env = process.env) {
  return {
    port: Number(env.PORT ?? 4300),
    dataDir: env.SHOP_DATA ?? DATA_DIR,
    // 상품 이미지를 가져와도 되는 서버(쉼표로 구분)
    imageHosts: (env.SHOP_IMAGE_HOSTS ?? 'images.daon.example').split(',').map((h) => h.trim()).filter(Boolean),
    // ✏️ 6-3: 비밀 값이 비어 있으면 알려진 기본값으로 조용히 돈다
    secret: env.SHOP_SECRET || 'keyboard cat',
  };
}
