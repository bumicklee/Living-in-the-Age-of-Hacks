package com.macro.mall.security.securekit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.lab.LabClock;
import com.macro.mall.security.token.JwtSigner;
import com.macro.mall.security.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
 * ②' 폐기  ✏️ 12-4: 토큰 jti가 폐기 목록(Redis deny:, 4-8과 같은 목록)에 있나   있으면 401 + partner.denied.revoked(WARN)
 * ③ 만료   exp(15분)가 지났나                               지났으면 401 + partner.denied.token_expired(INFO)
 * ③' 바인딩 ✏️ 12-3: 토큰의 cnf(x5t#S256) = 이 요청의 인증서 지문   다르거나 없으면 401 + token.denied.binding_mismatch(WARN)
 * ④ 범위   @PartnerScope 값이 토큰 scope에 있나              없으면 403 + partner.denied.scope(WARN)
 * </pre>
 *
 * - 토큰 발급(POST /partner/token)·비밀값 회전은 ①만 거친다(@PartnerScope가 없다. 비밀값으로 확인한다).
 * - 응답 본문은 OAuth 모양({"error":"invalid_token"} 등)이고 토큰 원문은 로그·응답에 넣지 않는다.
 * - [랩 스위치] partner-scope를 끄면 ④를 건너뛴다(컨트롤러도 배정 조건을 뺀다 = 시작 지점처럼 모든 주문). ①②③은 그대로.
 * - ✏️ 12-3: 무기명 토큰 → 소유 증명 토큰. 토큰을 가진 것만으로는 안 되고, 발급 때 쓴 인증서의 개인키도 가져야 한다.
 *   [랩 스위치] token-binding을 끄면 ③'을 건너뛴다(무기명 토큰).
 * - ✏️ 12-4: 앱 단위 일괄 폐기(관리자 앱 POST /daon/partner/apps/{id}/revoke, make partner-revoke)가 그 앱의 발급 토큰 jti를
 *   폐기 목록에 넣는다. 비밀값만 지우면 이미 발급한 토큰이 수명(15분) 동안 계속 쓰이기 때문이다. Redis가 응답하지 않으면 막는다.
 *   [랩 스위치] partner-revoke를 끄면 ②'를 건너뛴다.
 */
@Component
public class PartnerScopeInterceptor implements HandlerInterceptor {
    public static final String AUDIENCE = "daon-partner";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private JwtSigner signer;
    @Autowired
    private TokenService tokens;

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
        Object jti = claims.get("jti");
        if (SecureKitSwitch.on("partner-revoke") && (jti == null || tokens.isRevoked(String.valueOf(jti)))) {
            SecurityEventLogger.denied("partner.denied.revoked", "12-4").request(request).actor(actor).target(path)
                    .reason(jti == null ? "jti 없는 파트너 토큰" : "폐기한 앱의 토큰이 다시 쓰임(도난 토큰 사용 신호)").log();
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "폐기된 토큰");
        }
        Object exp = claims.get("exp");
        if (!(exp instanceof Number n) || n.longValue() <= LabClock.nowSeconds()) {
            SecurityEventLogger.suspicious("partner.denied.token_expired", "12-2").request(request)
                    .actor(actor).target(path).reason("만료된 파트너 토큰(재발급 전이면 정상, 많으면 재사용 의심)").log();
            return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "토큰 만료");
        }
        String cnf = cnf(claims.get("cnf"));
        if (SecureKitSwitch.on("token-binding")) {
            PartnerClientCert.Info cert = PartnerClientCert.of(request);
            boolean same = cnf != null && cert != null && MessageDigest.isEqual(
                    cnf.getBytes(StandardCharsets.US_ASCII), cert.thumbprint().getBytes(StandardCharsets.US_ASCII));
            if (!same) {
                SecurityEventLogger.denied("token.denied.binding_mismatch", "12-3").request(request).actor(actor).target(path)
                        .reason(cnf == null ? "인증서에 묶이지 않은 토큰"
                                : cert == null ? "요청에 확인된 클라이언트 인증서 없음" : "토큰에 묶인 인증서와 요청 인증서가 다름").log();
                return reject(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "인증서 바인딩 불일치");
            }
        }
        Set<String> scopes = scopes(claims.get("scope"));
        if (SecureKitSwitch.on("partner-scope") && !scopes.contains(need.value())) {
            SecurityEventLogger.denied("partner.denied.scope", "12-2").request(request)
                    .actor(actor).target(path).reason("범위 밖 기능 호출(필요한 범위 " + need.value() + ")").log();
            return reject(response, HttpServletResponse.SC_FORBIDDEN, "insufficient_scope", "범위 밖 기능");
        }
        request.setAttribute(PartnerPrincipal.ATTR, new PartnerPrincipal(appId, scopes, jti == null ? null : String.valueOf(jti), cnf));
        return true;
    }

    /** 토큰의 cnf 클레임 {"x5t#S256": "…"} → 지문. 없으면 null */
    static String cnf(Object cnf) {
        if (cnf instanceof Map<?, ?> m && m.get("x5t#S256") != null) {
            return String.valueOf(m.get("x5t#S256"));
        }
        return null;
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
