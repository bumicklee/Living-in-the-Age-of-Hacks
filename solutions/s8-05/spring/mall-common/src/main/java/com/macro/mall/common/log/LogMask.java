package com.macro.mall.common.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [다온마켓 8-5] 로그에 남기면 안 되는 값을 가리는 규칙 한 곳(점검표 F35·F39, T21).
 *
 * 세 겹 그물 중 앞의 두 겹이 쓴다.
 *   1. 원천 줄이기: WebLogAspect가 응답 본문을 남기지 않고, 요청 인자는 허용 목록 밖 값을 ***로(allowed)
 *   2. 앱 변환기: MaskingConverter가 모든 로그 줄의 메시지에서 패턴을 가림(mask). 놓친 것을 잡는 마지막 그물이다
 *   3. 수집기: infra/observability/alloy/config.alloy가 같은 패턴을 한 번 더(가렸으면 log.leak.masked_at_collector)
 *
 * mall-common은 mall-security(SecureKitSwitch)를 모른다. 방어 스위치 log-mask의 값은 mall-security의 LogMaskBridge가
 * 앱이 뜰 때 여기에 넣는다(기본 켜짐).
 */
public final class LogMask {
    private static volatile boolean enabled = true;

    /** JWT 모양: eyJ로 시작하는 점 세 마디 */
    static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    /** 휴대전화 모양: 01x-xxxx-xxxx(앞뒤가 영문·숫자가 아닐 때만. 주문 번호·trace_id·해시 같은 긴 글자 속 숫자는 그대로) */
    static final Pattern PHONE = Pattern.compile("(?<![0-9A-Za-z])01[016789]-?[0-9]{3,4}-?[0-9]{4}(?![0-9A-Za-z])");
    /** "password":"값" 꼴(JSON)과 password=값 꼴 */
    static final Pattern SECRET_JSON = Pattern.compile(
            "(\"(?:password|newPassword|oldPassword|confirmPassword|token|refreshToken|authCode|code)\"\\s*:\\s*\")([^\"]*)(\")",
            Pattern.CASE_INSENSITIVE);
    static final Pattern SECRET_KV = Pattern.compile("((?:password|refreshToken|token)=)([^&\\s,}\\]]+)", Pattern.CASE_INSENSITIVE);

    /** 요청 인자에서 값을 그대로 남겨도 되는 이름(개인정보·비밀이 아닌 것). 나머지는 이름만 남기고 값은 *** */
    public static final java.util.Set<String> ALLOWED_PARAMS = java.util.Set.of(
            "pageNum", "pageSize", "id", "ids", "status", "sort", "type", "productId", "orderId", "couponId",
            "useStatus", "productCategoryId", "brandId", "publishStatus", "verifyStatus", "orderType", "sourceType");

    private LogMask() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static String mask(String s) {
        if (s == null || s.isEmpty() || !enabled) {
            return s;
        }
        String out = replaceGroup(SECRET_JSON, s, 2, "***");
        out = replaceGroup(SECRET_KV, out, 2, "***");
        out = JWT.matcher(out).replaceAll("eyJ***");
        out = PHONE.matcher(out).replaceAll("010-****-****");
        return out;
    }

    private static String replaceGroup(Pattern p, String s, int group, String with) {
        Matcher m = p.matcher(s);
        if (!m.find()) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        int last = 0;
        do {
            sb.append(s, last, m.start(group)).append(with);
            last = m.end(group);
        } while (m.find());
        return sb.append(s.substring(last)).toString();
    }
}
