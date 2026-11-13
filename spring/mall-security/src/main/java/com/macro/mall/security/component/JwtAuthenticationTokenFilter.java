package com.macro.mall.security.component;

import com.macro.mall.security.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.secret.CanaryTokens;
import com.macro.mall.security.token.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT로그인 인증 필터
 * Created by macro on 2018/4/26.
 */
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private TokenService tokenService;
    @Value("${jwt.tokenHeader}")
    private String tokenHeader;
    @Value("${jwt.tokenHead}")
    private String tokenHead;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String authHeader = request.getHeader(this.tokenHeader);
        if (authHeader != null && authHeader.startsWith(this.tokenHead)
                && SecureKitSwitch.on("secret-canary")
                && CanaryTokens.isCanary(authHeader.substring(this.tokenHead.length()))) {
            SecurityEventLogger.denied("secret.canary_used", "6-2")
                    .request(request)
                    .target(CanaryTokens.label(authHeader.substring(this.tokenHead.length())))
                    .reason("미끼 자격증명이 쓰임(저장소나 문서가 밖으로 샜을 수 있음)")
                    .log();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"사용할 수 없는 자격증명입니다\"}");
            return;
        }
        if (authHeader != null && authHeader.startsWith(this.tokenHead)) {
            String authToken = authHeader.substring(this.tokenHead.length());// The part after "Bearer "
            String username = jwtTokenUtil.getUserNameFromToken(authToken);
            // 다온마켓 8-5(F39): 요청마다 사용자 이름을 INFO로 남기지 않는다(DEBUG). 스위치 log-mask를 끄면 옛 모습
            if (com.macro.mall.common.log.LogMask.enabled()) {
                LOGGER.debug("checking username:{}", username);
            } else {
                LOGGER.info("checking username:{}", username);
            }
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                // 다온마켓 4-8: 서명·만료에 더해 「로그아웃(폐기)한 토큰인가」를 묻는다. jti가 없는 예전 토큰은 받지 않는다
                if (jwtTokenUtil.validateToken(authToken, userDetails) && notRevoked(authToken)) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    if (com.macro.mall.common.log.LogMask.enabled()) {
                        LOGGER.debug("authenticated user:{}", username);
                    } else {
                        LOGGER.info("authenticated user:{}", username);
                    }
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean notRevoked(String token) {
        if (!SecureKitSwitch.on("token-revoke")) {
            return true;
        }
        String jti = jwtTokenUtil.getJti(token);
        if (jti == null) {
            return false;
        }
        if (tokenService.isRevoked(jti)) {
            SecurityEventLogger.denied("auth.token.revoked_used", "4-8")
                    .target("jti:" + jti.substring(0, Math.min(8, jti.length())))
                    .reason("로그아웃(폐기)한 액세스 토큰이 다시 쓰임")
                    .log();
            return false;
        }
        return true;
    }
}
