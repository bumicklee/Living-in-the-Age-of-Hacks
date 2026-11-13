// 수업 시험: npm run check 2-2  (앱을 켜지 않아도 된다)
import { LESSONS, lessonArg } from './lessons.js';
import { createHarness } from './harness.js';

const id = lessonArg();
const quiet = process.argv.includes('--quiet');
const { default: checks } = await import(`../checks/${id}.js`);

if (!quiet) console.log(`${id} 수업 시험: ${LESSONS[id].title}`);
let passed = 0;
for (const c of checks) {
  let error = null;
  const h = c.noHarness ? null : await createHarness({ env: c.env });
  try {
    await c.run(h);
  } catch (err) {
    error = err;
  } finally {
    await h?.close();
  }
  if (!error) {
    passed += 1;
    if (!quiet) console.log(`  ✔ ${c.name}`);
  } else if (!quiet) {
    console.log(`  ✘ ${c.name}`);
    console.log(`     이유: ${error.message}`);
    if (c.hint) console.log(`     힌트: ${c.hint}`);
  }
}
console.log(`${quiet ? id + ' ' : ''}결과: ${passed === checks.length ? '통과' : '실패'} ${passed}/${checks.length}`);
process.exitCode = passed === checks.length ? 0 : 1;
