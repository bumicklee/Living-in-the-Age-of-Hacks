package io.github.securekit.web;

import io.github.securekit.core.SecureKitException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Set;

/** secure-kit 토큰으로 확인한 사람·파트너. 토큰 필터가 요청 속성 secure-kit.principal에 넣는다 */
public final class KitPrincipal {
    private final String sub;
    private final List<String> roles;
    private final String audience;
    private final Set<String> scopes;
    private final String jti;
    private final long exp;

    public KitPrincipal(String sub, List<String> roles, String audience, Set<String> scopes, String jti, long exp) {
        this.sub = sub;
        this.roles = List.copyOf(roles);
        this.audience = audience;
        this.scopes = Set.copyOf(scopes);
        this.jti = jti;
        this.exp = exp;
    }

    public String sub() { return sub; }
    public List<String> roles() { return roles; }
    public String audience() { return audience; }
    public Set<String> scopes() { return scopes; }
    public String jti() { return jti; }
    public long exp() { return exp; }

    public boolean isPartner() {
        return "partner".equals(audience);
    }

    public String actor() {
        if (isPartner()) return "partner:" + sub;
        return (roles.contains("admin") ? "admin:" : "user:") + sub;
    }

    /** 사람 로그인(aud = kit 등 파트너가 아닌 것)이 필요하다. 없으면 401 */
    public static KitPrincipal requireUser(HttpServletRequest request) {
        KitPrincipal p = KitContext.principal(request);
        if (p == null || p.isPartner()) throw new SecureKitException(401, "로그인이 필요합니다");
        return p;
    }

    /** 역할이 없으면 403(권한 확인은 각 기능 코드에서 한다. 이 도우미는 역할만 본다) */
    public KitPrincipal requireRole(String role) {
        if (!roles.contains(role)) throw new SecureKitException(403, "권한이 없습니다");
        return this;
    }
}
