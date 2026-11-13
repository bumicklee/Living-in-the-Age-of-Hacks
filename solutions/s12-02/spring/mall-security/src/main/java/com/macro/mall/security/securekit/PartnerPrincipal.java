package com.macro.mall.security.securekit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * [다온마켓 12-2 정답] 확인을 마친 파트너 토큰의 주인. PartnerScopeInterceptor가 요청 속성에 넣고 컨트롤러가 꺼내 쓴다.
 *
 * @param appId  파트너 앱 ID(토큰의 partner_id). 조회 조건에 이 값을 넣는다
 * @param scopes 토큰에 든 범위
 * @param jti    토큰 ID(12-4 일괄 폐기가 이 값으로 끊는다)
 * @param cnf    묶인 인증서 지문(12-3, 없으면 null)
 */
public record PartnerPrincipal(String appId, Set<String> scopes, String jti, String cnf) {
    public static final String ATTR = "daon.partner.principal";

    /** 이벤트 actor 칸 */
    public String actor() {
        return "partner:" + appId;
    }

    public static PartnerPrincipal of(HttpServletRequest request) {
        Object p = request.getAttribute(ATTR);
        if (p instanceof PartnerPrincipal pp) {
            return pp;
        }
        throw new IllegalStateException("@PartnerScope가 없는 파트너 API입니다");
    }
}
