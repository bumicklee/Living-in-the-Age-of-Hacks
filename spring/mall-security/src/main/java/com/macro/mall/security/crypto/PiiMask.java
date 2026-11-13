package com.macro.mall.security.crypto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [다온마켓 5-3] 화면·캐시에 둘 때 쓰는 가림 값. 앞 부분과 뒤 네 자리만 남긴다.
 *
 * <pre>
 * PiiMask.phone("010-0000-0101") → "010-****-0101"
 * PiiMask.phone("01000000101")   → "010****0101"
 * </pre>
 *
 * 5-4에서 관리자 응답에도 같은 함수를 쓴다(응답을 만드는 서버에서 가린다. 브라우저에서 가리면 응답에는 원문이 남는다).
 */
public final class PiiMask {
    private static final Pattern PHONE = Pattern.compile("^(\\d{2,3})(-?)(\\d{3,4})(-?)(\\d{4})$");

    private PiiMask() {
    }

    public static String phone(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher m = PHONE.matcher(value);
        if (m.matches()) {
            return m.group(1) + m.group(2) + "****" + m.group(4) + m.group(5);
        }
        int keep = Math.min(4, value.length());
        return "*".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }
}
