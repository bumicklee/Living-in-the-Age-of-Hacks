package com.macro.mall.security.web;

import com.macro.mall.security.event.SecurityEventLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * [다온마켓 6-5] 로그인 없이 관리·문서 경로를 두드리는 요청을 기록한다(막은 뒤의 탐지). 경로를 막는 것은 이 필터가 아니다.
 * 한 번은 흔한 봇이라 INFO. 같은 IP가 서로 다른 관리 경로를 여러 개 두드리면 count로 드러나고, 8장에서 경보로 올린다.
 * 경로 목록은 한 곳(PATHS)에 둔다. target에는 경로만(쿼리·값 없음, 120자까지).
 */
public class EndpointProbeFilter extends OncePerRequestFilter {
    static final List<String> PATHS = List.of("/actuator", "/druid", "/swagger-ui", "/v3/api-docs", "/.env", "/.git", "/heapdump", "/env", "/phpmyadmin");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (request.getHeader("Authorization") == null && path != null && PATHS.stream().anyMatch(p -> path.startsWith(p))) {
            SecurityEventLogger.suspicious("recon.endpoint_probe", "6-5")
                    .request(request)
                    .actor("anonymous")
                    .target("path:" + (path.length() > 120 ? path.substring(0, 120) : path))
                    .reason("로그인 없이 관리·문서 경로 요청")
                    .log();
        }
        chain.doFilter(request, response);
    }
}
