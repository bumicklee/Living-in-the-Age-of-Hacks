// 앱 실행: npm start
import { loadConfig } from './config.js';
import { openDb } from './db.js';
import { createApp } from './app.js';
import { startLabWorld } from '../lab-world/index.js';

let config;
try {
  config = loadConfig();
} catch (err) {
  console.error(`시작할 수 없습니다: ${err.message}`);
  process.exit(1);
}

const world = await startLabWorld({
  imagesPort: Number(process.env.LAB_IMAGES_PORT ?? 4398),
  internalPort: Number(process.env.LAB_INTERNAL_PORT ?? 4399),
});
const db = openDb(config.dataDir);
const app = createApp({ config, db, labNet: world.net });

app.listen(config.port, '127.0.0.1', () => {
  console.log(`다온마켓 Express 미니: http://127.0.0.1:${config.port}`);
  console.log(`랩 세계: 이미지 서버 images.daon.example, 사내 관리 서버 inside.daon.example:${world.internalPort}`);
});
