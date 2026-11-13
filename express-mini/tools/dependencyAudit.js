import { spawnSync } from 'node:child_process';
import { DEPENDENCY_WORK } from './files.js';

// npm 등록소가 돌려준 실제 감사 결과를 읽는다. 네트워크 오류는 통과로 취급하지 않는다.
export function auditDependencyWork() {
  const result = spawnSync('npm', ['audit', '--json'], {
    cwd: DEPENDENCY_WORK, encoding: 'utf8', timeout: 60_000,
  });
  if (result.error) throw result.error;
  let report;
  try { report = JSON.parse(result.stdout); }
  catch { throw new Error('npm 감사 결과를 읽을 수 없습니다. 인터넷 연결을 확인하세요.'); }
  if (report.error) throw new Error(`npm 감사 실패: ${report.error.summary ?? report.error.code}`);
  const counts = report.metadata?.vulnerabilities;
  if (!counts || ![0, 1].includes(result.status)) {
    throw new Error('npm 감사가 끝나지 않았습니다. 인터넷 연결을 확인하세요.');
  }
  return counts;
}
