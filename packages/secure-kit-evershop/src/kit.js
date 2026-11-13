// 확장 전체가 같이 쓰는 kit 하나. 설정 키는 Spring·Express와 같은 secure-kit.* 이다.
// [뼈대] EverShop 설정(config/*.json)에서 읽는 방법은 확인하지 않았다. 지금은 환경 변수 SECURE_KIT_CONFIG(JSON)만 읽는다.
import { secureKit } from '@secure-kit/express';

const DEFAULT_CONFIG = {
  'secure-kit': {
    headers: { enabled: true },
    'error-response': { enabled: true },
    // EverShop 로그인 본문은 email 로 계정을 보낸다
    'rate-limit': { login: { 'per-account': '5/10m', 'per-ip': '50/10m', 'account-field': 'email' } }
  }
};

function loadConfig() {
  const raw = process.env.SECURE_KIT_CONFIG;
  if (!raw) return DEFAULT_CONFIG;
  try {
    return JSON.parse(raw);
  } catch {
    throw new Error('secure-kit-evershop: SECURE_KIT_CONFIG가 JSON이 아닙니다');
  }
}

export const kit = secureKit(loadConfig());
