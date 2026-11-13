package io.github.securekit.web.token;

import io.github.securekit.core.SecureKitException;
import io.github.securekit.web.KitContext;
import io.github.securekit.web.error.KitErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization: Bearer &lt;토큰&gt; 이 있으면 확인하고 요청 속성 secure-kit.principal에 넣는다.
 * 틀린 토큰은 여기서 401로 끝낸다(틀린 토큰을 「로그인 안 함」으로 흘려보내지 않는다).
 * 로그인이 꼭 필요한지는 각 기능이 KitPrincipal.requireUser()로 정한다.
 */
public class KitTokenFilter extends OncePerRequestFilter {
    private final KitTokenService tokens;

    public KitTokenFilter(KitTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            chain.doFilter(request, response);
            return;
        }
        try {
            request.setAttribute(KitContext.PRINCIPAL_ATTR, tokens.verify(auth.substring(7).trim()));
        } catch (SecureKitException e) {
            KitErrorWriter.write(request, response, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
