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
 * [다온마켓 6-6] 허용 목록 밖 출처의 교차 출처 요청을 기록한다(막는 것은 CorsFilter, 이 필터는 보기만).
 * 브라우저 확장·잘못된 설정에서도 생기므로 INFO. 같은 출처가 반복되면 8장에서 확인한다.
 * 출처 값은 공격자가 넣은 문자열일 수 있다 → 120자까지만, 화면에는 textContent로만(랩-설계 13절).
 */
public class OriginWatchFilter extends OncePerRequestFilter {
    private final List<String> allowed;

    public OriginWatchFilter(List<String> allowed) {
        this.allowed = allowed;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowed.contains(origin) && !sameOrigin(request, origin)) {
            SecurityEventLogger.suspicious("cors.denied_origin", "6-6")
                    .request(request)
                    .actor("anonymous")
                    .target("origin:" + (origin.length() > 120 ? origin.substring(0, 120) : origin))
                    .reason("허용 목록 밖 출처의 교차 출처 요청")
                    .log();
        }
        chain.doFilter(request, response);
    }

    private static boolean sameOrigin(HttpServletRequest request, String origin) {
        return origin.equals(request.getScheme() + "://" + request.getHeader("Host"));
    }
}
