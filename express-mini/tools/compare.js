// 내 코드와 정답 비교: npm run compare 2-2
// 수업에서 고치는 파일만, 정답과 다른 줄을 보여 준다(- 내 코드, + 정답).
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { LESSONS, lessonArg } from './lessons.js';
import { ROOT, SOLUTIONS } from './files.js';

const color = process.stdout.isTTY ? { red: '\x1b[31m', green: '\x1b[32m', dim: '\x1b[2m', off: '\x1b[0m' } : { red: '', green: '', dim: '', off: '' };

// 두 줄 목록의 공통 부분(LCS)을 구해 바뀐 줄만 고른다
function diff(a, b) {
  const n = a.length;
  const m = b.length;
  const lcs = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(0));
  for (let i = n - 1; i >= 0; i -= 1) for (let j = m - 1; j >= 0; j -= 1) {
    lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
  }
  const out = [];
  let i = 0;
  let j = 0;
  while (i < n || j < m) {
    if (i < n && j < m && a[i] === b[j]) { out.push([' ', a[i]]); i += 1; j += 1; }
    else if (i < n && (j === m || lcs[i + 1][j] >= lcs[i][j + 1])) { out.push(['-', a[i]]); i += 1; }
    else { out.push(['+', b[j]]); j += 1; }
  }
  return out;
}

const id = lessonArg();
let same = true;
for (const rel of LESSONS[id].files) {
  const mineFile = join(ROOT, rel);
  const answerFile = id === '18-4'
    ? join(ROOT, 'extras/18-4/solution', rel.split('/').at(-1))
    : join(SOLUTIONS, id, rel);
  const mine = existsSync(mineFile) ? readFileSync(mineFile, 'utf8').split('\n') : [];
  const answer = readFileSync(answerFile, 'utf8').split('\n');
  const rows = diff(mine, answer);
  const changed = rows.map((r, k) => (r[0] === ' ' ? -1 : k)).filter((k) => k >= 0);
  console.log(`${rel}`);
  if (changed.length === 0) { console.log('  정답과 같습니다'); continue; }
  same = false;
  const show = new Set(changed.flatMap((k) => [k - 1, k, k + 1]));
  let last = -2;
  rows.forEach(([mark, text], k) => {
    if (!show.has(k)) return;
    if (k !== last + 1) console.log(`${color.dim}  …${color.off}`);
    const c = mark === '-' ? color.red : mark === '+' ? color.green : color.dim;
    console.log(`${c}${mark} ${text}${color.off}`);
    last = k;
  });
}
console.log(same ? '모든 파일이 정답과 같습니다' : '(- 내 코드, + 정답) 정답과 다른 줄이 있습니다. 다른 방법으로 고쳤다면 npm run check 로 확인하세요');
