package com.macro.mall.security.component;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.security.config.IgnoreUrlsConfig;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.OpsElevationGate;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 동적 인가 관리자, 리소스 접근 권한 여부 판단에 사용
 * Created by macro on 2023/11/3.
 *
 * [다온마켓 13-6 정답] 역할로 통과한 요청도 고위험 기능(OpsElevationGate.HIGH_RISK)이면 살아 있는 권한 상승이 있어야 한다.
 * 없으면 403 + ops.denied.no_elevation. 권한 상승 요청·활성화(/daon/elevation/**)는 로그인한 관리자 누구나 부른다.
 */
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Autowired
    private DynamicSecurityMetadataSource securityDataSource;
    @Autowired
    private IgnoreUrlsConfig ignoreUrlsConfig;
    @Autowired(required = false)
    private OpsElevationGate elevationGate;              // 관리자 앱만 준다(mall-portal에는 없다)

    @Override
    public void verify(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        AuthorizationManager.super.verify(authentication, object);
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext requestAuthorizationContext) {
        HttpServletRequest request = requestAuthorizationContext.getRequest();
        String path = request.getRequestURI();
        PathMatcher pathMatcher = new AntPathMatcher();
        //화이트리스트 경로는 바로 통과
        List<String> ignoreUrls = ignoreUrlsConfig.getUrls();
        for (String ignoreUrl : ignoreUrls) {
            if (pathMatcher.match(ignoreUrl, path)) {
                return new AuthorizationDecision(true);
            }
        }
        //CORS 사전 요청(preflight)은 바로 통과
        if(request.getMethod().equals(HttpMethod.OPTIONS.name())){
            return new AuthorizationDecision(true);
        }
        //권한 검증 로직
        List<ConfigAttribute> configAttributeList = securityDataSource.getConfigAttributesWithPath(path);
        List<String> needAuthorities = configAttributeList.stream()
                .map(ConfigAttribute::getAttribute)
                .collect(Collectors.toList());
        Authentication currentAuth = authentication.get();
        boolean loggedIn = currentAuth != null && currentAuth.isAuthenticated() && !(currentAuth instanceof AnonymousAuthenticationToken);
        // 다온마켓 13-6: 권한 상승 요청·활성화는 로그인한 관리자 누구나(권한표에 올리지 않는다)
        if (elevationGate != null && pathMatcher.match(OpsElevationGate.SELF_SERVICE, path)) {
            return new AuthorizationDecision(loggedIn);
        }
        // 다온마켓 2-3: 기본 거부. 권한표에 없는 주소는 누구에게도 열지 않는다
        if (needAuthorities.isEmpty()) {
            if (!SecureKitSwitch.on("admin-default-deny")) {
                // [랩 스위치] secure-kit.admin-default-deny.enabled=false: 고치기 전처럼 로그인만 했으면 통과(영상용)
                return new AuthorizationDecision(currentAuth != null && currentAuth.isAuthenticated()
                        && !(currentAuth instanceof AnonymousAuthenticationToken));
            }
            denied(currentAuth, request, path, "권한표에 없는 주소(기본 거부)");
            return new AuthorizationDecision(false);
        }
        //로그인 인증이 이미 구현되었는지 판단
        if(currentAuth.isAuthenticated()){
            Collection<? extends GrantedAuthority> grantedAuthorities = currentAuth.getAuthorities();
            List<? extends GrantedAuthority> hasAuth = grantedAuthorities.stream()
                    .filter(item -> needAuthorities.contains(item.getAuthority()))
                    .collect(Collectors.toList());
            if(CollUtil.isNotEmpty(hasAuth)){
                // 다온마켓 13-6: 역할이 있어도 고위험 기능은 시간 제한 권한 상승이 살아 있을 때만
                String group = OpsElevationGate.groupOf(request.getMethod(), path);
                if (group != null && elevationGate != null && SecureKitSwitch.on("ops-elevation")
                        && !elevationGate.isActive(currentAuth.getName(), group)) {
                    SecurityEventLogger.denied("ops.denied.no_elevation", "13-6")
                            .actor("admin:" + currentAuth.getName())
                            .target("path:" + path)
                            .reason("고위험 기능(" + group + ")을 권한 상승 없이 호출")
                            .request(request)
                            .log();
                    return new AuthorizationDecision(false);
                }
                return new AuthorizationDecision(true);
            }else{
                denied(currentAuth, request, path, "역할에 이 주소 권한이 없음");
                return new AuthorizationDecision(false);
            }
        }else{
            return new AuthorizationDecision(false);
        }
    }

    // 다온마켓 2-3 ⑤ 탐지: 로그인한 관리자가 권한 밖 기능을 부르면 남긴다(익명 요청은 401로 따로 끝난다)
    private void denied(Authentication auth, HttpServletRequest request, String path, String reason) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return;
        }
        SecurityEventLogger.denied("access.denied.no_permission", "2-3")
                .actor("admin:" + auth.getName())
                .target("path:" + path)
                .reason(reason)
                .request(request)
                .log();
    }
}
