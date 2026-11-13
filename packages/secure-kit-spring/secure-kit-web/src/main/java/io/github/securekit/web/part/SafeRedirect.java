package io.github.securekit.web.part;

import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.redirect.RedirectRules;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 위험한 이동 주소 막기(오픈 리다이렉트, 11-2). 바꿔 쓰기: response.sendRedirect(next) 대신 safeRedirect.redirect(next).
 *
 * 통과: 우리 사이트 안 상대 경로, 허용 호스트(secure-kit.safe-redirect.allowed-hosts)의 https 주소.
 * 나머지(//evil, https://evil, /\evil, javascript:)는 "/"로 보내고 redirect.denied.not_allowed를 남긴다.
 */
public class SafeRedirect {
    private final Set<String> allowedHosts = new HashSet<>();

    public SafeRedirect(Collection<String> hosts) {
        for (String h : hosts) allowedHosts.add(h.toLowerCase(Locale.ROOT));
    }

    /** 써도 되는 이동 주소. 안 되면 "/" */
    public String target(String next) {
        if (RedirectRules.isSafe(next, allowedHosts)) return next;
        // 받은 값은 이벤트에 넣지 않는다(피싱 주소 모음이 로그에 쌓이지 않게)
        SecurityEvents.warn("redirect.denied.not_allowed", "safe-redirect", "param:next", "허용 밖 이동 주소 → /");
        return "/";
    }

    /** 302 응답(Location은 그대로 둔다. 상대 경로를 절대 주소로 바꾸지 않는다) */
    public ResponseEntity<Void> redirect(String next) {
        HttpHeaders h = new HttpHeaders();
        h.set(HttpHeaders.LOCATION, target(next));
        return new ResponseEntity<>(h, HttpStatus.FOUND);
    }
}
