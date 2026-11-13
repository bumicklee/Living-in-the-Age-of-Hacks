package com.macro.mall.portal.config;

import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * [다온마켓 11-4 정답 · secure-kit cache-control] 캐시 여부는 오리진이 정한다.
 *
 * - 로그인한 요청(Authorization 헤더가 있음)의 응답: Cache-Control: private, no-store
 *   (공유 캐시·브라우저 모두 저장하지 않음. 캐시 키에는 「누가」가 없다)
 * - 로그인 없는 공개 상품 목록(GET): Cache-Control: public, max-age=60 (공유 캐시가 60초 저장해도 되는 응답이라고 밝힌다)
 * - 그 밖: Spring Security 기본값(no-cache, no-store, max-age=0, must-revalidate) 그대로
 *
 * 응답을 쓰기 전에 헤더를 먼저 정해 둔다. Spring Security의 캐시 헤더 작성기는 Cache-Control이 이미 있으면 덮지 않는다.
 * 캐시 쪽(Varnish VCL)도 함께 고친다: 두 겹 중 한 겹만 고치면 다음에 누가 다른 겹을 바꿀 때 다시 뚫린다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class DaonCacheHeaderFilter extends OncePerRequestFilter {
    /** 공개(로그인 없는) 상품 목록·상세. 회원별로 달라지지 않는 응답만 */
    private static final List<String> PUBLIC_PREFIXES = List.of("/product/search", "/product/categoryTreeList", "/product/detail/",
            "/daon/product/search");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecureKitSwitch.on("private-cache")) {
            String auth = request.getHeader("Authorization");
            if (auth != null && !auth.isBlank()) {
                response.setHeader("Cache-Control", "private, no-store");
            } else if ("GET".equals(request.getMethod()) && isPublic(request.getRequestURI())) {
                response.setHeader("Cache-Control", "public, max-age=60");
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isPublic(String uri) {
        for (String p : PUBLIC_PREFIXES) {
            if (p.endsWith("/") ? uri.startsWith(p) : uri.equals(p)) {     // 「/」로 끝나면 그 아래 전부, 아니면 정확히 같은 주소만
                return true;
            }
        }
        return false;
    }
}
