// 부품 17 limits(느린 정규식) · kit.safeRegex()  (수업 9-3)
// 사용자 입력에 정규식을 쓸 때 new RegExp(...).test(v) 대신 이것으로 바꾼다(③).
// 1) 정규식에 넣기 「전에」 길이를 자른다(되돌아가기 폭주는 입력 길이에 따라 커진다)
// 2) 만들 때 (a+)+ · (.*)* 처럼 겹친 반복 모양을 거부한다(간단한 모양 검사. 완전한 판정은 아니다)
//   const coupon = kit.safeRegex('^[A-Z0-9]+(-[A-Z0-9]+)*$', { maxLength: 20, name: 'coupon' })
//   coupon.test(req, value)   // 길면 KitHttpError(400) + input.denied.too_long
import { KitHttpError } from './http.js';

const PART = 'limits';
// 괄호 안이 「반복 하나」뿐이고 괄호 밖에 또 반복: (a+)+ (\w*)* ([a-z]+){2,}
const NESTED_QUANTIFIER = /\((?:\?:)?(?:\\.|\[[^\]]*\]|[^()\\])[+*]\)[+*{]/;

export function createSafeRegex({ events, pattern, opts = {} }) {
  const source = pattern instanceof RegExp ? pattern.source : String(pattern);
  if (NESTED_QUANTIFIER.test(source)) {
    throw new Error(`secure-kit: 겹친 반복이 있는 정규식은 쓰지 않습니다: ${source}`);
  }
  const re = new RegExp(source, pattern instanceof RegExp ? pattern.flags : opts.flags ?? '');
  const maxLength = opts.maxLength ?? 64;
  const name = opts.name ?? 'input';

  /** 형식이 맞으면 true. 길이 초과는 형식 문제가 아니라 공격 신호라 오류로 던진다 */
  function test(req, value) {
    const v = typeof value === 'string' ? value : '';
    if (v.length > maxLength) {
      events.emit(req, { event: 'input.denied.too_long', part: PART, target: `field:${name}`, reason: `길이 ${maxLength}자 초과` });
      throw new KitHttpError(400, '입력이 너무 깁니다');
    }
    return re.test(v);
  }

  return { test, source, maxLength };
}
