package com.macro.mall.security.web;

import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [다온마켓 6-6] 모든 응답에 보안 헤더를 붙인다(같은 정책을 앱마다 빠뜨리지 않게 한 곳에 모은 필터).
 * 정책: nosniff · Referrer-Policy · X-Frame-Options · Permissions-Policy. CSP는 3-5(관리자 화면 nginx)에 있다.
 * HSTS는 HTTPS 입구에서만 붙인다(랩의 127.0.0.1 HTTP에는 붙이지 않는다).
 * 스위치 secure-kit.sec-headers.enabled=false면 붙이지 않는다(영상용).
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (SecureKitSwitch.on("sec-headers")) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        }
        chain.doFilter(request, response);
    }
}
