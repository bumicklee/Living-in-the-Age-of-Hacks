package com.macro.mall.security.limits;

import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [다온마켓 9-3 정답, F41] 목록 API의 pageSize 상한(100). 넘으면 400(조용히 자르지 않는다: 클라이언트 버그가 드러나게).
 *
 * 두 앱의 목록 컨트롤러가 pageSize를 PageHelper에 그대로 넘긴다(SQL LIMIT). 컨트롤러마다 @Max를 붙이는 대신
 * 주소창 값(쿼리 문자열)의 pageSize를 한 곳에서 본다. 관리자 내보내기처럼 큰 페이지가 필요한 기능은 다른 경로로 둔다.
 */
public class PageSizeLimitFilter extends OncePerRequestFilter {
    static final int MAX = 100;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String raw = SecureKitSwitch.on("input-limits") ? queryParam(request.getQueryString(), "pageSize") : null;
        if (raw != null && tooBig(raw)) {
            SecurityEventLogger.denied("input.denied.page_size", "9-3")
                    .request(request)
                    .target("path:" + RequestSizeLimitFilter.safePath(request.getRequestURI()))
                    .reason("페이지 크기 범위(1~" + MAX + ") 밖")
                    .log();
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().println("{\"code\":400,\"message\":\"pageSize는 1~" + MAX + "입니다\"}");
            response.getWriter().flush();
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean tooBig(String raw) {
        String v = raw.trim();
        if (!v.matches("-?\\d{1,12}")) {
            return false;                 // 숫자가 아니면 Spring이 400(형 변환 오류)으로 답한다
        }
        long n = Long.parseLong(v);
        return n > MAX || n < 1;
    }

    /** 본문을 건드리지 않으려고 쿼리 문자열만 읽는다(getParameter는 폼 본문까지 읽는다) */
    static String queryParam(String query, String name) {
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            if (k.equals(name)) {
                return eq < 0 ? "" : java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
