package io.github.securekit.web.part;

import io.github.securekit.core.Hashes;
import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.core.store.CounterStore;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * 재설정 링크 오염 막기(4-5). 바꿔 쓰기: request.getHeader("Host")로 링크를 만들지 않는다.
 *
 * - 링크 = 설정 secure-kit.reset-link.base-url + /reset?token=… (요청의 Host·X-Forwarded-Host는 쓰지 않는다).
 *   X-Forwarded-Host가 기준과 다르면 auth.reset.host_mismatch를 남긴다(오염 시도 흔적).
 * - 토큰: 무작위 32바이트, 저장소에는 해시만, 15분, 한 번만. 다시 쓰면 400 + auth.reset.token_reused.
 */
public class ResetLinkBuilder {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String baseUrl;
    private final String baseHost;
    private final CounterStore store;
    private final Duration ttl;

    public ResetLinkBuilder(String baseUrl, CounterStore store, Duration ttl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseHost = URI.create(this.baseUrl).getHost();
        this.store = store;
        this.ttl = ttl;
    }

    /** 링크를 만든다. 메일은 부른 쪽이 보낸다(가입된 경우에만) */
    public String issue(String username, HttpServletRequest request) {
        String forwarded = request == null ? null : request.getHeader("X-Forwarded-Host");
        if (forwarded != null && !forwarded.split(":")[0].trim().equalsIgnoreCase(baseHost)) {
            String shown = forwarded.length() > 64 ? forwarded.substring(0, 64) : forwarded;
            SecurityEvents.warn("auth.reset.host_mismatch", "reset-link", "host:" + shown, "X-Forwarded-Host가 기준 주소와 다르다(무시함)");
        }
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        store.put(tokenKey(token), username, ttl);
        return baseUrl + "/reset?token=" + token;
    }

    /** 토큰을 쓴다(한 번만). 주인 아이디를 돌려준다 */
    public String consume(String token) {
        if (token == null || token.isBlank()) throw invalid();
        String username = store.get(tokenKey(token));
        if (username == null) {
            if (store.get(usedKey(token)) != null) {
                SecurityEvents.warn("auth.reset.token_reused", "reset-link", "token:" + Hashes.short8(token), "이미 쓴 재설정 토큰");
            } else {
                SecurityEvents.warn("auth.reset.token_invalid", "reset-link", "token:" + Hashes.short8(token), "없거나 만료된 재설정 토큰");
            }
            throw invalid();
        }
        if (!store.putIfAbsent(usedKey(token), "1", Duration.ofDays(1))) {
            SecurityEvents.warn("auth.reset.token_reused", "reset-link", "token:" + Hashes.short8(token), "동시에 두 번 쓰려 했다");
            throw invalid();
        }
        store.delete(tokenKey(token));
        return username;
    }

    private static SecureKitException invalid() {
        return new SecureKitException(400, "링크가 만료되었거나 이미 사용되었습니다");
    }

    private static String tokenKey(String token) {
        return "reset:token:" + Hashes.sha256Hex(token);
    }

    private static String usedKey(String token) {
        return "reset:used:" + Hashes.sha256Hex(token);
    }
}
