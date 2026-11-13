// 전체 확인: npm run verify-all
// 수업 순서를 섞어 「시작 상태는 시험 실패 → 정답은 시험 통과 → 다른 수업으로 옮기면 앞 수업 정답이 남지 않음」을 확인한다.
// 고치던 app/ 은 먼저 보관하고, 끝나면 되돌려 놓는다.
import { spawnSync } from 'node:child_process';
import { cpSync, existsSync, rmSync } from 'node:fs';
import { join } from 'node:path';
import { LESSONS } from './lessons.js';
import { APP, DEPENDENCY_WORK, ROOT, applyLessonStart, applySolution, backupApp, restoreBase } from './files.js';

const rounds = Number(process.argv[2] ?? 1);
const check = (id) => spawnSync(process.execPath, ['--disable-warning=ExperimentalWarning', join(ROOT, 'tools/check.js'), id, '--quiet'], { encoding: 'utf8' });
const shuffle = (xs) => xs.map((x) => [Math.random(), x]).sort((a, b) => a[0] - b[0]).map(([, x]) => x);

const saved = backupApp();
let problems = 0;
try {
  for (let round = 1; round <= rounds; round += 1) {
    const order = shuffle(Object.keys(LESSONS));
    console.log(`${round}회차 순서: ${order.join(' → ')}`);
    let previous = null;
    for (const id of order) {
      restoreBase();
      applyLessonStart(id);
      const start = check(id);
      // 보너스는 앞 수업의 방어를 모두 합친 상태라 이전 정답이 통과하는 것이 정상이다.
      if (previous && !LESSONS[id].bonus && !LESSONS[id].extra && !LESSONS[previous].extra) {
        const again = check(previous);
        if (again.status === 0) { problems += 1; console.log(`  ✘ ${previous}: 다른 수업(${id})으로 옮겼는데 정답이 남아 시험이 통과합니다`); }
      }
      applySolution(id);
      const done = check(id);
      const ok = start.status !== 0 && done.status === 0;
      if (!ok) problems += 1;
      console.log(`  ${ok ? '✔' : '✘'} ${id} 시작 ${start.stdout.trim().split('\n').pop()} · 정답 ${done.stdout.trim().split('\n').pop()}`);
      if (done.status !== 0) console.log(done.stdout + done.stderr);
      previous = id;
    }
  }
} finally {
  rmSync(APP, { recursive: true, force: true });
  cpSync(join(saved, 'app'), APP, { recursive: true });
  rmSync(DEPENDENCY_WORK, { recursive: true, force: true });
  if (existsSync(join(saved, 'dependency-work'))) cpSync(join(saved, 'dependency-work'), DEPENDENCY_WORK, { recursive: true });
}
console.log(problems === 0 ? '전체 확인 통과' : `전체 확인 실패: 문제 ${problems}건`);
process.exitCode = problems === 0 ? 0 : 1;
