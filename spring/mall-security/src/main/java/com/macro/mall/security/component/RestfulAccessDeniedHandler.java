package com.macro.mall.security.component;

import cn.hutool.json.JSONUtil;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 권한 없는 접근에 대한 사용자 정의 반환 결과
 * Created by macro on 2018/4/26.
 *
 * 다온마켓 8-4(점검표 F32): 권한 없음은 HTTP 403으로 보낸다(본문 모양은 그대로). 거부 이벤트는 2-3의
 * DynamicAuthorizationManager가 이미 남긴다(access.denied.no_permission). 방어 스위치 status-codes를 끄면 옛 모습(HTTP 200).
 */
public class RestfulAccessDeniedHandler implements AccessDeniedHandler{
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        if (SecureKitSwitch.on("status-codes")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Cache-Control","no-cache");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().println(JSONUtil.parse(CommonResult.forbidden(e.getMessage())));
        response.getWriter().flush();
    }
}
