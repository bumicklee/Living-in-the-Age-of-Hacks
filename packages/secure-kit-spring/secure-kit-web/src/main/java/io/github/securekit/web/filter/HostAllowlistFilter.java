package io.github.securekit.web.filter;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.core.event.SecurityEvents;
import io.github.securekit.web.error.KitErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 낯선 Host 막기(DNS 리바인딩·Host 머리 오염, 2-6). 설정 secure-kit.host-allowlist.
 *
 * 공격자 도메인이 우리 서버 IP를 가리키게 바꾸면(리바인딩) 브라우저는 Host: 공격자도메인 으로 보낸다.
 * 허용 목록 밖 Host는 400 + request.denied.host. 포트는 보지 않는다.
 */
public class HostAllowlistFilter extends OncePerRequestFilter {
    private final Set<String> hosts = new HashSet<>();

    public HostAllowlistFilter(Collection<String> allowed) {
        for (String h : allowed) hosts.add(h.toLowerCase(Locale.ROOT));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getServerName() == null ? "" : request.getServerName().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (!hosts.contains(host)) {
            String shown = host.length() > 64 ? host.substring(0, 64) : host;
            SecurityEvents.warn("request.denied.host", "host-allowlist", "host:" + shown, "허용 목록 밖의 Host");
            KitErrorWriter.write(request, response, new SecureKitException(400, "허용하지 않는 주소입니다"));
            return;
        }
        chain.doFilter(request, response);
    }
}
