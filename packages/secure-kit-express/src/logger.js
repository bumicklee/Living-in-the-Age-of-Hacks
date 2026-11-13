// 부품 16 log-masking · kit.logger()
// 앱이 무엇을 찍든 쓰기 직전에 가린다: 전화번호 가운데, 비밀번호·토큰 같은 필드 값, JWT 모양 문자열.
// 실제로 쓴 줄(가린 뒤)을 최근 200줄 보관한다(랩 /_kit/logs).

const DEFAULT_FIELDS = ['password', 'passwd', 'pwd', 'token', 'secret', 'authorization', 'card', 'cardnumber', 'newpassword', 'accesstoken', 'refreshtoken'];
const PHONE = /\b(01[016789])[- .]?(\d{3,4})[- .]?(\d{4})\b/g;
const JWT = /\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g;
const BEARER = /\b(Bearer\s+)[A-Za-z0-9._~+/=-]+/gi;
const KEEP_LINES = 200;

export function maskPhone(text) {
  return String(text).replace(PHONE, (_, a, _b, c) => `${a}-****-${c}`);
}

export function maskText(text) {
  return maskPhone(String(text).replace(JWT, '[JWT]').replace(BEARER, '$1[TOKEN]'));
}

/** 객체를 JSON으로 바꾸면서 민감한 필드 이름의 값은 *** 로 바꾼다 */
function stringifyMasked(value, fields) {
  if (value instanceof Error) return `${value.name}: ${value.message}${value.stack ? `\n${value.stack}` : ''}`;
  if (typeof value !== 'object' || value === null) return String(value);
  try {
    return JSON.stringify(value, (k, v) => (k && fields.has(k.toLowerCase().replace(/[-_]/g, '')) ? '***' : v));
  } catch {
    return '[직렬화 실패]';
  }
}

export function createLogger(cfg = {}) {
  const fields = new Set([...DEFAULT_FIELDS, ...(cfg.fields ?? [])].map((f) => f.toLowerCase().replace(/[-_]/g, '')));
  const masking = cfg.enabled !== false; // enabled: false 는 영상 시연용(가리기 전 모습 보여 주기)
  const lines = [];
  const out = cfg.write ?? ((line) => process.stdout.write(`${line}\n`));

  function write(level, args) {
    const text = args.map((a) => (masking ? stringifyMasked(a, fields) : stringifyMasked(a, new Set()))).join(' ');
    const line = `${new Date().toISOString()} ${level} ${masking ? maskText(text) : text}`;
    lines.push(line);
    if (lines.length > KEEP_LINES) lines.shift();
    out(line);
  }

  return {
    info: (...args) => write('INFO', args),
    warn: (...args) => write('WARN', args),
    error: (...args) => write('ERROR', args),
    /** 보안 이벤트·감사 기록처럼 이미 모양이 정해진 줄. 그래도 가리기는 거친다 */
    raw: (level, text) => write(level, [text]),
    lines: () => [...lines]
  };
}
