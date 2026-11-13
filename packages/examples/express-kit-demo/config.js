// 예제 설정. 키는 Spring 예제(application.yml 의 secure-kit.*)와 같다. 모든 값은 랩 전용 가짜 값이다.
export const kitConfig = {
  'secure-kit': {
    'host-allowlist': ['127.0.0.1', 'localhost', 'shop.example'],
    'safe-redirect': { 'allowed-hosts': ['shop.example'] },
    upload: { 'max-size': '2mb' },
    headers: { enabled: true },
    cors: { 'allowed-origins': ['http://shop.example'] },
    'rate-limit': { login: { 'per-account': '5/10m', 'per-ip': '50/10m' } },
    'password-policy': {
      'min-length': 10,
      'breached-list': ['password1234', 'qwerty123456', 'daon-pass-1!', '1q2w3e4r5t', 'iloveyou123']
    },
    'reset-link': { 'base-url': 'http://shop.example', ttl: '15m' },
    'step-up': { header: 'X-Reauth-Password' },
    token: { secret: 'kit-lab-fake-token-secret-not-for-prod-32', kid: 'k1', audience: 'kit', ttl: '15m' },
    'field-crypto': { key: 'kit-lab-fake-field-key-not-for-prod', 'index-key': 'kit-lab-fake-index-key-not-for-prod' },
    'error-response': { enabled: true },
    audit: { sink: 'memory' },
    'log-masking': { enabled: true, fields: ['phone-raw', 'card'] },
    limits: { 'body-max': '1mb', routes: { '/kit/upload': '3mb' } },
    'resilient-client': {
      retries: 1,
      'retry-budget': { ratio: 0.1, 'min-per-minute': 3 },
      circuit: { 'failure-threshold': 5, 'open-for': '20s' }
    },
    webhook: { secret: 'kit-lab-fake-webhook-secret', tolerance: '5m' },
    budget: { sms: { 'per-target': '1/60s' } },
    'cache-control': { authenticated: 'no-store' },
    approval: { policies: { 'refund-high-value': { threshold: 300000 } } },
    lab: { expose: process.env.KIT_LAB_EXPOSE !== 'false' }
  }
};

// 데모 사용자(PBKDF2 해시. 비밀번호는 CONTRACT.md 의 가짜 값)
export const demoUsers = [
  { id: 1, sub: 'kit-user', roles: ['user'], email: 'kit-user@shop.example', passwordHash: 'pbkdf2-sha256$100000$6omNBgM+CXef8RbfLRJ7AA==$MAQ7JIJZFsyjYan70TKiZ4jwK1khlH87kJWiEbWUtmc=' },
  { id: 2, sub: 'kit-admin', roles: ['admin'], email: 'kit-admin@shop.example', passwordHash: 'pbkdf2-sha256$100000$tAet91+DV6JZ5EnN3S1Utw==$erfi0wREoJsLKGNi8NTt+kFypr+5jpP3s0R6vSs7kAw=' }
];

// 가짜 회원 25명. 전화번호는 가짜 모양 010-0000-NNNN 만 쓴다
export const members = Array.from({ length: 25 }, (_, i) => ({
  id: i + 1,
  name: `회원${String(i + 1).padStart(2, '0')}`,
  phone: `010-0000-${String(1001 + i).padStart(4, '0')}`
}));
