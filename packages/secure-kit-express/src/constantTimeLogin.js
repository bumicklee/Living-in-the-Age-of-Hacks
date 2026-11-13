// 부품 8 constant-time-login · kit.constantTimeLogin()  (수업 4-4)
// 「없는 아이디」와 「틀린 비밀번호」가 같은 응답·비슷한 시간이 되게 한다(③ 로그인 확인을 이것으로 바꾼다).
// 없는 아이디도 같은 비용의 가짜 해시를 계산한다. 비교는 timingSafeEqual.
// 해시 모양: pbkdf2-sha256$<반복 수>$<소금 base64>$<해시 base64>
import { pbkdf2, randomBytes, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';

const pbkdf2Async = promisify(pbkdf2);
const DEFAULT_ITERATIONS = 100_000;
const KEY_LEN = 32;

export async function hashPassword(password, iterations = DEFAULT_ITERATIONS) {
  const salt = randomBytes(16);
  const dk = await pbkdf2Async(String(password), salt, iterations, KEY_LEN, 'sha256');
  return `pbkdf2-sha256$${iterations}$${salt.toString('base64')}$${dk.toString('base64')}`;
}

function parseHash(stored) {
  const [scheme, iter, salt, hash] = String(stored ?? '').split('$');
  if (scheme !== 'pbkdf2-sha256' || !iter || !salt || !hash) return null;
  return { iterations: Number(iter), salt: Buffer.from(salt, 'base64'), hash: Buffer.from(hash, 'base64') };
}

export async function verifyPassword(password, stored) {
  const h = parseHash(stored);
  if (!h) return false;
  const dk = await pbkdf2Async(String(password ?? ''), h.salt, h.iterations, h.hash.length, 'sha256');
  return dk.length === h.hash.length && timingSafeEqual(dk, h.hash);
}

/**
 * @param findUser  async (username) => ({ ...user, passwordHash }) | null   앱의 사용자 조회
 */
export function createConstantTimeLogin({ findUser, iterations = DEFAULT_ITERATIONS }) {
  // 없는 아이디용 가짜 해시(진짜와 같은 반복 수). 기동할 때 한 번 만든다
  const dummy = hashPassword(`dummy-${randomBytes(8).toString('hex')}`, iterations);

  /** 맞으면 사용자, 아니면 null. 어느 쪽이든 해시 계산을 한 번 한다 */
  async function login(username, password) {
    const user = typeof username === 'string' && username.length <= 128 ? await findUser(username) : null;
    const stored = user?.passwordHash ?? (await dummy);
    const ok = await verifyPassword(typeof password === 'string' ? password : '', stored);
    return ok && user ? user : null;
  }

  return { login, hashPassword: (pw) => hashPassword(pw, iterations), verifyPassword };
}
