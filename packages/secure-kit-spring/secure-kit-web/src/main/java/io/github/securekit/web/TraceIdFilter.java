package io.github.securekit.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 모든 요청에 추적 번호를 붙인다. 응답 머리 X-Trace-Id · 오류 본문 traceId · 보안 이벤트 trace_id가 같은 값이다.
 * 들어온 X-Trace-Id가 [A-Za-z0-9._-]{8,64}이면 그대로 쓰고, 아니면 새로 만든다(로그 주입을 막는다).
 * 가장 먼저 도는 필터다.
 */
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Id";
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String traceId = incoming != null && VALID.matcher(incoming).matches() ? incoming : newId();
        request.setAttribute(KitContext.TRACE_ATTR, traceId);
        response.setHeader(HEADER, traceId);
        KitContext.bind(request);
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
            KitContext.unbind();
        }
    }

    private static String newId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return "t-" + HexFormat.of().formatHex(b);
    }
}
