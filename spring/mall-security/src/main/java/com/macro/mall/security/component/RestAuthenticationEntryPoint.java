package com.macro.mall.security.component;

import cn.hutool.json.JSONUtil;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 미로그인 또는 token 만료 시 사용자 정의 반환 결과
 * Created by macro on 2018/5/14.
 *
 * 다온마켓 8-4(점검표 F32): 원본은 본문만 code 401이고 HTTP 상태는 200이었다. 게이트웨이·모니터링은 상태 코드로 실패를 세므로
 * 실패가 0으로 보였다. 이제 HTTP 401을 보내고(본문 모양은 그대로), SECURITY auth.denied.token을 남긴다(토큰 값은 남기지 않는다).
 * 방어 스위치 status-codes를 끄면 옛 모습(HTTP 200, 이벤트 없음).
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        if (SecureKitSwitch.on("status-codes")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            String auth = request.getHeader("Authorization");
            SecurityEventLogger.denied("auth.denied.token", "8-4")
                    .request(request)
                    .actor("anonymous")
                    .target("path:" + path(request))
                    .reason(auth == null || auth.isBlank() ? "토큰 없음" : "토큰 만료·서명 불일치")
                    .log();
        }
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Cache-Control","no-cache");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().println(JSONUtil.parse(CommonResult.unauthorized(authException.getMessage())));
        response.getWriter().flush();
    }

    /** 경로는 요청한 사람이 정한 글자다. 영문·숫자·/._- 만 남기고 120자까지(로그 위조 막기) */
    private static String path(HttpServletRequest request) {
        String p = String.valueOf(request.getRequestURI()).replaceAll("[^A-Za-z0-9/._-]", "_");
        return p.length() > 120 ? p.substring(0, 120) : p;
    }
}
