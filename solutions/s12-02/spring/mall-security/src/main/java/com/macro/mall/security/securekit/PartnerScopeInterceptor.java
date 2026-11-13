package com.macro.mall.security.securekit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.token.JwtSigner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * [다온마켓 12-2 정답 · secure-kit 부품] 파트너 경로(/partner/**)의 문지기. mall-portal의 DaonPartnerWebConfig가 등록한다.
 *
 * <pre>
 * ① 입구   파트너 입구(게이트웨이 8087)에서 왔나            아니면 404(PartnerEntrance)
 * ② 토큰   Authorization: Bearer, 서명·kid(5-2 키 체계)·aud=daon-partner·partner_id   아니면 401
 * ③ 만료   exp(15분)가 지났나                               지났으면 401 + partner.denied.token_expired(INFO)
 * ④ 범위   @PartnerScope 값이 토큰 scope에 있나              없으면 403 + partner.denied.scope(WARN)
 * </pre>
 *
 * - 토큰 발급(POST /partner/token)·비밀값 회전은 ①만 거친다(@PartnerScope가 없다. 비밀값으로 확인한다).
 * - 응답 본문은 OAuth 모양({"error":"invalid_token"} 등)이고 토큰 원문은 로그·응답에 넣지 않는다.
 * - [랩 스위치] partner-scope를 끄면 ④를 건너뛴다(컨트롤러도 배정 조건을 뺀다 = 시작 지점처럼 모든 주문). ①②③은 그대로.
 */
@Component
public class PartnerScopeInterceptor implements HandlerInterceptor {
    public static final String AUDIENCE = "daon-partner";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JwtSigner signer;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!PartnerEntrance.fromGateway(request)) {
            return reject(response, HttpServletResponse.SC_NOT_FOUND, "not_found", null);
        }
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }
        PartnerScope need = hm.getMethodAnnotation(PartnerScope.class);
        if (need == null) {
            return true;
        }
        String path = "path:" + request.getRequestURI();
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "토큰 없음");
        }
        Map<String, Object> claims = signer.verify(auth.substring(7).trim(), AUDIENCE);
        Object partnerId = claims == null ? null : claims.get("partner_id");
        if (claims == null || partnerId == null || !AUDIENCE.equals(claims.get("aud"))) {
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "파트너 토큰이 아님");
        }
        String appId = String.valueOf(partnerId);
        String actor = "partner:" + appId;
        Object exp = claims.get("exp");
        if (!(exp instanceof Number n) || n.longValue() <= LabClock.nowSeconds()) {
            SecurityEventLogger.suspicious("partner.denied.token_expired", "12-2").request(request)
                    .actor(actor).target(path).reason("만료된 파트너 토큰(재발급 전이면 정상, 많으면 재사용 의심)").log();
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "토큰 만료");
        }
        Set<String> scopes = scopes(claims.get("scope"));
        if (SecureKitSwitch.on("partner-scope") && !scopes.contains(need.value())) {
            SecurityEventLogger.denied("partner.denied.scope", "12-2").request(request)
                    .actor(actor).target(path).reason("범위 밖 기능 호출(필요한 범위 " + need.value() + ")").log();
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "insufficient_scope", "범위 밖 기능");
        }
        Object jti = claims.get("jti");
        request.setAttribute(PartnerPrincipal.ATTR, new PartnerPrincipal(appId, scopes, jti == null ? null : String.valueOf(jti), null));
        return true;
    }

    static Set<String> scopes(Object scope) {
        if (scope == null) {
            return Set.of();
        }
        return Arrays.stream(String.valueOf(scope).split(" ")).filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean reject(HttpServletResponse response, int status, String error, String description) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        if (description != null) {
            body.put("error_description", description);
        }
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer error=\"" + error + "\"");
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(JSON.writeValueAsString(body));
        return false;
    }
}
