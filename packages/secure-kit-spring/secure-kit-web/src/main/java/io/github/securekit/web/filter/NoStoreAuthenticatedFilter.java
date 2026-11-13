package io.github.securekit.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 로그인한 응답은 캐시에 두지 않는다(11-4). 설정 secure-kit.cache-control.enabled.
 *
 * Authorization 머리(또는 설정한 세션 쿠키)가 있는 요청의 응답은 Cache-Control: no-store.
 * 컨트롤러가 public 캐시를 붙여도 덮어쓴다(CDN·프록시가 남의 개인정보를 다른 사람에게 주는 사고).
 * 로그인 없는 공개 응답은 건드리지 않는다.
 */
public class NoStoreAuthenticatedFilter extends OncePerRequestFilter {
    private static final String NO_STORE = "no-store";
    private final List<String> sessionCookies;

    public NoStoreAuthenticatedFilter(List<String> sessionCookies) {
        this.sessionCookies = List.copyOf(sessionCookies);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!authenticated(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("Cache-Control", NO_STORE);
        response.setHeader("Pragma", "no-cache");
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void setHeader(String name, String value) {
                super.setHeader(name, isCacheControl(name) ? NO_STORE : value);
            }

            @Override
            public void addHeader(String name, String value) {
                if (isCacheControl(name)) super.setHeader(name, NO_STORE);
                else super.addHeader(name, value);
            }
        });
    }

    private boolean authenticated(HttpServletRequest request) {
        if (request.getHeader("Authorization") != null) return true;
        if (request.getCookies() == null) return false;
        for (var c : request.getCookies()) if (sessionCookies.contains(c.getName())) return true;
        return false;
    }

    private static boolean isCacheControl(String name) {
        return "Cache-Control".equalsIgnoreCase(name);
    }
}
