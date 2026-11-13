package com.macro.mall.daon;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [교육용 추가] 운영 도구 포트(8095)에는 /ops/**만, 관리자 API 포트(8080)에는 /ops/**를 빼고 연다. 수업 2-6(T05).
 */
@Component
public class DaonOpsGuardFilter extends OncePerRequestFilter {
    @Value("${daon.ops.port:8095}")
    private int opsPort;

    // 다온마켓 2-6: 이 도구를 부를 때 쓰는 이름만 허용한다(compose가 DAON_OPS_HOSTS로 넣는다)
    @Value("${daon.ops.hosts:127.0.0.1:8095,localhost:8095}")
    private String allowedHostsConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean onOpsPort = request.getLocalPort() == opsPort;
        boolean opsPath = request.getRequestURI().startsWith("/ops/");
        if (onOpsPort != opsPath) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // 다온마켓 2-6: Host 허용 목록. DNS 리바인딩으로 들어온 요청은 공격자 이름을 Host에 달고 온다
        // [랩 스위치] secure-kit.host-allowlist.enabled(기본 켜짐). 끄면 고치기 전처럼 Host를 보지 않는다(영상용)
        if (onOpsPort && SecureKitSwitch.on("host-allowlist")) {
            String host = request.getHeader("Host");
            if (host == null || !allowedHosts().contains(host.trim().toLowerCase(Locale.ROOT))) {
                // ⑤ 탐지: 낯선 Host. 이름은 64자까지만 남긴다
                SecurityEventLogger.denied("request.denied.host", "2-6")
                        .actor("anonymous")
                        .target("host:" + (host == null ? "-" : host.length() > 64 ? host.substring(0, 64) : host))
                        .reason("허용하지 않은 Host")
                        .request(request)
                        .log();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"허용하지 않은 Host입니다\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private Set<String> allowedHosts() {
        return Arrays.stream(allowedHostsConfig.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
