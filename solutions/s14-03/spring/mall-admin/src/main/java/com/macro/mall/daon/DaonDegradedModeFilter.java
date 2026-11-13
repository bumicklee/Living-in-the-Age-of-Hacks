package com.macro.mall.daon;

import com.macro.mall.security.securekit.DegradedMode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * [다온마켓 14-3 정답] 축소 운영이면 돈이 나가는 관리자 기능을 503 + 안내 문구로 멈춘다.
 *
 * 멈추는 것: 반품 확인(환불 승인 요청, /returnApply/update/status/**) · 승인 토큰 환불 실행(/daon/refund/**).
 * 이유: 결제사 상태를 확신할 수 없는 동안 돈이 나가는 쪽을 멈추는 것이 더 안전하다(이미 처리된 환불을 다시 보내는 일을 막는다).
 * 조회(GET)는 막지 않는다. 주문 접수는 고객 앱이 대기 큐로 받는다(「주문은 받고 정산은 멈춘다」).
 * Spring Security 필터 뒤에 돈다(로그인·권한표는 먼저 본다).
 */
@Component
public class DaonDegradedModeFilter extends OncePerRequestFilter {
    static final List<String> MONEY_OUT = List.of("/returnApply/update/status/", "/daon/refund/");
    static final String MESSAGE = "{\"code\":503,\"message\":\"결제 확인이 늦어지고 있어 환불·정산을 잠시 멈췄습니다(축소 운영). 주문 조회는 그대로 됩니다\"}";

    @Autowired
    private DegradedMode degradedMode;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!"GET".equalsIgnoreCase(request.getMethod()) && MONEY_OUT.stream().anyMatch(path::startsWith)
                && degradedMode.isOn(DegradedMode.PAYMENT)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Retry-After", "300");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(MESSAGE);
            return;
        }
        chain.doFilter(request, response);
    }
}
