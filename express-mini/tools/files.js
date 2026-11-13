// 작업 폴더(app/)를 다루는 도우미.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = fileURLToPath(new URL('..', import.meta.url));
export const APP = join(ROOT, 'app');
export const BASE = join(ROOT, 'base', 'app');
export const SOLUTIONS = join(ROOT, 'solutions');
export const DATA = join(ROOT, 'data');
export const ENV = join(ROOT, '.env');
export const DEPENDENCY_WORK = join(ROOT, 'extras', '18-4', 'work');

function copyDependencyFixture(phase) {
  rmSync(DEPENDENCY_WORK, { recursive: true, force: true });
  mkdirSync(DEPENDENCY_WORK, { recursive: true });
  for (const name of ['package.json', 'package-lock.json']) {
    cpSync(join(ROOT, 'extras', '18-4', phase, name), join(DEPENDENCY_WORK, name));
  }
}

// 고치던 app/을 .backup/시각/ 에 남긴다
export function backupApp() {
  const d = new Date();
  const two = (n) => String(n).padStart(2, '0');
  const stamp = `${d.getFullYear()}${two(d.getMonth() + 1)}${two(d.getDate())}-${two(d.getHours())}${two(d.getMinutes())}${two(d.getSeconds())}`;
  const dir = join(ROOT, '.backup', stamp);
  mkdirSync(dir, { recursive: true });
  cpSync(APP, join(dir, 'app'), { recursive: true });
  if (existsSync(DEPENDENCY_WORK)) cpSync(DEPENDENCY_WORK, join(dir, 'dependency-work'), { recursive: true });
  return dir;
}

// app/을 처음(모든 빈틈이 있는) 상태로
export function restoreBase() {
  rmSync(APP, { recursive: true, force: true });
  cpSync(BASE, APP, { recursive: true });
}

// 정답 파일을 app/ 위에 덮는다
export function applySolution(id) {
  if (id === '18-4') return copyDependencyFixture('solution');
  const dir = join(SOLUTIONS, id, 'app');
  if (!existsSync(dir)) throw new Error(`정답 폴더가 없습니다: solutions/${id}`);
  cpSync(dir, APP, { recursive: true });
}

// 수업별 시작 파일이 있으면 공통 원본 위에 덮는다.
export function applyLessonStart(id) {
  if (id === '18-4') return copyDependencyFixture('start');
  const dir = join(ROOT, 'lessons', id, 'app');
  if (existsSync(dir)) cpSync(dir, APP, { recursive: true });
}

export function readEnv() {
  if (!existsSync(ENV)) return {};
  return Object.fromEntries(readFileSync(ENV, 'utf8').split('\n')
    .filter((l) => /^[A-Z_]+=/.test(l)).map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
}

export function writeEnv(values) {
  const lines = ['# 다온마켓 Express 미니 설정(내 PC 전용, 저장소에 올리지 않는다)'];
  for (const [k, v] of Object.entries(values)) lines.push(`${k}=${v}`);
  writeFileSync(ENV, lines.join('\n') + '\n');
}
