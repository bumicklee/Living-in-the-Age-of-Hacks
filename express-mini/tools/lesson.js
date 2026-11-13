// 수업 시작: npm run lesson 2-2
import { LESSONS, lessonArg } from './lessons.js';
import { applyLessonStart, backupApp, readEnv, restoreBase, writeEnv } from './files.js';
import { resetData } from './dbtool.js';
import { relative } from 'node:path';
import { ROOT } from './files.js';

const id = lessonArg();
const lesson = LESSONS[id];
const saved = backupApp();
restoreBase();
applyLessonStart(id);
if (lesson.env) writeEnv({ ...readEnv(), ...lesson.env });
resetData();
console.log(`${id} 시작 지점 준비 끝: ${lesson.title}`);
console.log(`고칠 파일: ${lesson.files.join(', ')}`);
console.log(`고치던 파일은 ${relative(ROOT, saved)} 에 보관했습니다.`);
console.log(`다음: npm run replay ${id}`);
