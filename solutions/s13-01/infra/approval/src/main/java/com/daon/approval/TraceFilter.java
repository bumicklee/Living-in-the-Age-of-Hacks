package com.daon.approval;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * [A10] 요청 추적 번호(랩-설계 11절 규칙을 승인 서비스에도). 관리자 앱이 X-Trace-Id로 넘긴 값을 그대로 쓴다.
 * 모양이 이상한 값(줄바꿈·따옴표 등)은 버리고 새로 만든다. 응답 헤더에도 같은 값을 준다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "trace_id";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String v = req.getHeader(HEADER);
        String id = v != null && SAFE.matcher(v.trim()).matches() ? v.trim() : newId();
        MDC.put(MDC_KEY, id);
        res.setHeader(HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
