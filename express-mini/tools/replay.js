// 재연: npm run replay 2-2  (다른 터미널에서 npm start 로 앱을 켜 두어야 한다)
import { LESSONS, lessonArg } from './lessons.js';
import { appIsUp, BASE_URL } from './http.js';

const id = lessonArg();
if (id !== '18-4' && !(await appIsUp())) {
  console.log(`앱이 꺼져 있습니다(${BASE_URL}). 다른 터미널에서 npm start 를 먼저 실행하세요.`);
  process.exit(2);
}
const { default: replay } = await import(`../replay/${id}.js`);
console.log(`${id} 재연: ${LESSONS[id].title}`);
const result = await replay();
for (const line of result.lines) console.log(`  ${line}`);
console.log(result.red ? `레드: ${result.verdict}` : `그린: ${result.verdict}`);
process.exitCode = result.red ? 1 : 0;
