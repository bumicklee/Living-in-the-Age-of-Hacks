package com.macro.mall.daon;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [교육용 추가] 운영 도구 포트(8095)에는 /ops/**만, 관리자 API 포트(8080)에는 /ops/**를 빼고 연다. 수업 2-6(T05).
 */
@Component
public class DaonOpsGuardFilter extends OncePerRequestFilter {
    @Value("${daon.ops.port:8095}")
    private int opsPort;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean onOpsPort = request.getLocalPort() == opsPort;
        boolean opsPath = request.getRequestURI().startsWith("/ops/");
        if (onOpsPort != opsPath) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        // ✏️ 2-6: 내 PC에서만 열리는 도구라고 보고 요청의 Host를 확인하지 않는다(다온마켓 교육용 표시)
        chain.doFilter(request, response);
    }
}
