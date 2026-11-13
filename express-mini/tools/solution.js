// 정답 적용(막혔을 때): npm run solution 2-2
import { LESSONS, lessonArg } from './lessons.js';
import { applySolution, backupApp, restoreBase, ROOT } from './files.js';
import { relative } from 'node:path';

const id = lessonArg();
const saved = backupApp();
restoreBase();
applySolution(id);
console.log(`${id} 정답을 적용했습니다: ${LESSONS[id].files.join(', ')}`);
console.log(`고치던 파일은 ${relative(ROOT, saved)} 에 보관했습니다.`);
console.log(`다음: npm run replay ${id} → npm run check ${id}`);
