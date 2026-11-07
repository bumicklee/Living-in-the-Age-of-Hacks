package com.macro.mall.security.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [다온마켓 추가] 모든 요청의 맨 앞에서 추적 번호를 정한다(TraceIdConfig가 가장 먼저 돌게 등록한다).
 * Spring Security·다온 운영 도구 필터보다 앞이라, 거기서 남기는 SECURITY 이벤트도 같은 번호를 쓴다.
 */
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = TraceId.accept(request.getHeader(TraceId.HEADER));
        if (id == null) {
            id = TraceId.fromTraceparent(request.getHeader("traceparent"));
        }
        if (id == null) {
            id = TraceId.accept(request.getHeader("X-Request-Id"));
        }
        if (id == null) {
            id = TraceId.newId();
        }
        request.setAttribute(TraceId.ATTR, id);
        response.setHeader(TraceId.HEADER, id);      // 오류 응답에도 붙도록 처리 전에 단다
        MDC.put(TraceId.MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceId.MDC_KEY);             // 스레드를 다음 요청이 다시 쓴다
        }
    }
}
