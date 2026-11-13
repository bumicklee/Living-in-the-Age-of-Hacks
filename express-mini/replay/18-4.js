import { auditDependencyWork } from '../tools/dependencyAudit.js';

export default async function () {
  const counts = auditDependencyWork();
  return {
    red: counts.total > 0,
    lines: [`npm audit: 전체 ${counts.total}건, 높음 ${counts.high}건, 치명적 ${counts.critical}건`],
    verdict: counts.total > 0
      ? '잠금 파일에 알려진 취약점이 있는 의존성이 남아 있습니다'
      : '잠금 파일의 의존성 감사에서 알려진 취약점이 나오지 않았습니다',
  };
}
