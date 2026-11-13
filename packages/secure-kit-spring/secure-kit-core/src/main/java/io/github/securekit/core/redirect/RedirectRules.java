package io.github.securekit.core.redirect;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * 로그인 뒤 이동 주소(next) 판정. 통과하는 것은 두 가지뿐이다.
 *
 * 1. 우리 사이트 안 상대 경로: "/"로 시작하고 "//"·"/\"로 시작하지 않는다(브라우저가 다른 호스트로 읽는다).
 * 2. 허용한 호스트의 https 주소(사용자 정보 user@ 없음).
 *
 * 역슬래시·제어 문자가 하나라도 있으면 막는다(브라우저마다 다르게 고쳐 읽는다).
 */
public final class RedirectRules {
    private RedirectRules() {
    }

    public static boolean isSafe(String next, Set<String> allowedHosts) {
        if (next == null || next.isEmpty() || next.length() > 2048) return false;
        for (char c : next.toCharArray()) {
            if (c < 0x20 || c == 0x7f || c == '\\') return false;
        }
        if (next.startsWith("/")) {
            return !next.startsWith("//");
        }
        try {
            URI u = new URI(next);
            String host = u.getHost();
            return "https".equalsIgnoreCase(u.getScheme())
                    && host != null
                    && u.getRawUserInfo() == null
                    && allowedHosts.contains(host.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            return false;
        }
    }
}
