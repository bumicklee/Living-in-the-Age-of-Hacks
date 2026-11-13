package io.github.securekit.web.filter;

import io.github.securekit.core.event.SecurityEvents;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 보안 헤더·CSP·CORS(3-5·6-6). 설정 secure-kit.headers · secure-kit.cors.
 *
 * 헤더(모든 응답): X-Content-Type-Options nosniff · X-Frame-Options DENY · Referrer-Policy no-referrer ·
 * Content-Security-Policy(기본 default-src 'self'; frame-ancestors 'none'; …). X-Powered-By는 지운다.
 *
 * CORS: 허용 목록에 **정확히** 있는 Origin에만 Access-Control-Allow-Origin을 그 값으로 준다(* 금지, 반사 금지).
 * 목록 밖 Origin은 막지 않고(응답은 그대로) 허용 머리를 주지 않으며 cors.denied_origin(INFO)을 남긴다.
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {
    public static final String DEFAULT_CSP =
            "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'";

    private final boolean headers;
    private final String csp;
    private final List<String> allowedOrigins;
    private final String allowedMethods;
    private final String allowedHeaders;

    public SecurityHeadersFilter(boolean headers, String csp, List<String> allowedOrigins,
                                 List<String> allowedMethods, List<String> allowedHeaders) {
        this.headers = headers;
        this.csp = csp == null || csp.isBlank() ? DEFAULT_CSP : csp;
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.allowedMethods = String.join(", ", allowedMethods);
        this.allowedHeaders = String.join(", ", allowedHeaders);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (headers) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "no-referrer");
            response.setHeader("Content-Security-Policy", csp);
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.isEmpty()) {
            response.addHeader("Vary", "Origin");
            if (allowedOrigins.contains(origin)) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                if ("OPTIONS".equals(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
                    response.setHeader("Access-Control-Allow-Methods", allowedMethods);
                    response.setHeader("Access-Control-Allow-Headers", allowedHeaders);
                    response.setHeader("Access-Control-Max-Age", "600");
                    response.setStatus(204);
                    return;
                }
            } else {
                String shown = origin.length() > 64 ? origin.substring(0, 64) : origin;
                SecurityEvents.info("cors.denied_origin", "headers", "origin:" + shown, "허용 목록 밖 Origin(허용 머리를 주지 않음)");
            }
        }
        chain.doFilter(request, response);
    }
}
