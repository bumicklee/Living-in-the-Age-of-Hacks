import { auditDependencyWork } from '../tools/dependencyAudit.js';
import { expectTrue } from '../tools/expect.js';

export default [
  {
    name: '잠금 파일 기준 npm 감사에서 취약점이 0건이다',
    noHarness: true,
    hint: 'package.json과 package-lock.json을 함께 갱신한 뒤 npm audit 결과를 확인하세요.',
    async run() {
      const counts = auditDependencyWork();
      expectTrue(counts.total === 0, `npm 감사 결과: 전체 ${counts.total}건, 높음 ${counts.high}건`);
    },
  },
];
