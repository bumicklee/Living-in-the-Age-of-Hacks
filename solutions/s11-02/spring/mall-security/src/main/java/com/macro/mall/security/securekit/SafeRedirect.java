package com.macro.mall.security.securekit;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * [다온마켓 11-2 정답 · secure-kit 부품] 이동 주소를 **한 번만** 파싱하고, 그 결과 객체로 검사와 이동을 모두 한다.
 *
 * <pre>
 * SafeRedirect.Decision d = SafeRedirect.resolve(next, Set.of("shop.daon.example"));
 * return ResponseEntity.status(302).location(d.location()).build();   // 검사한 바로 그 URI 객체
 * </pre>
 *
 * 순서: new URI(next) 한 번(실패하면 거부) → normalize() → 규칙 검사 → 같은 URI 객체로 Location.
 * 규칙은 URI의 부품(스킴·호스트)과 함께 **내보낼 글자**(toASCIIString)에도 건다. Location에 들어가는 것은 그 글자다.
 * 원래 문자열은 다시 쓰지 않는다(검사는 객체로, 이동은 문자열로 하면 처음 문제로 돌아간다).
 *
 * 규칙 1(기본안, 상대 경로만): 스킴·호스트·사용자 정보가 모두 없고, 경로가 「/」 하나로 시작하고, 두 번째 글자가 「/」나 「\」가 아니다.
 * 규칙 2(외부 이동이 꼭 필요할 때): 스킴은 https만, 사용자 정보 없음, 호스트는 허용 목록과 정확히 같음(접미사 비교 금지), 포트 없음.
 * 거부하면 「/」(기본 화면)로 보낸다. 오류 화면은 띄우지 않는다.
 */
public final class SafeRedirect {
    public static final URI HOME = URI.create("/");

    /** location: 보낼 곳(거부면 HOME). deniedRule: 거부한 규칙 번호("0" 파싱 실패, "1" 상대 경로 규칙, "2" 외부 주소 규칙), 통과면 null */
    public record Decision(URI location, String deniedRule) {
        public boolean allowed() {
            return deniedRule == null;
        }
    }

    private SafeRedirect() {
    }

    public static Decision resolve(String next, Set<String> allowedHosts) {
        if (next == null || next.isBlank()) {
            return new Decision(HOME, null);                       // 이동 주소가 없으면 기본 화면(거부가 아니다)
        }
        URI u;
        try {
            u = new URI(next).normalize();                          // ① 한 번만 파싱 ② 정규화
        } catch (URISyntaxException e) {
            return new Decision(HOME, "0");                         // 파서가 못 읽는 주소는 거부(실패하면 막는다)
        }
        // 규칙은 「내보낼 글자」(u.toASCIIString(), Location에 그대로 들어간다)에도 건다.
        // 예: 「///x」는 URI가 경로 /x로 읽지만 글자는 ///x 그대로라, 브라우저는 x라는 호스트로 읽는다(부품만 보면 통과해 버린다)
        String loc = u.toASCIIString();
        if (u.getScheme() == null && u.getRawAuthority() == null) {
            boolean ok = loc.startsWith("/")
                    && !(loc.length() > 1 && (loc.charAt(1) == '/' || loc.charAt(1) == '\\'));
            return ok ? new Decision(u, null) : new Decision(HOME, "1");
        }
        String host = u.getHost();
        boolean ok = "https".equalsIgnoreCase(u.getScheme())
                && u.getRawUserInfo() == null
                && host != null
                && allowedHosts.contains(host.toLowerCase(Locale.ROOT))   // 정확히 같음. endsWith 금지
                && u.getPort() == -1
                && startsWithOrigin(loc, "https://" + host);
        return ok ? new Decision(u, null) : new Decision(HOME, "2");
    }

    /** 내보낼 글자가 「https://허용호스트」로 시작하고 바로 뒤가 끝·「/」·「?」·「#」인가 */
    private static boolean startsWithOrigin(String loc, String origin) {
        if (!loc.regionMatches(true, 0, origin, 0, origin.length())) {
            return false;
        }
        if (loc.length() == origin.length()) {
            return true;
        }
        char c = loc.charAt(origin.length());
        return c == '/' || c == '?' || c == '#';
    }
}
