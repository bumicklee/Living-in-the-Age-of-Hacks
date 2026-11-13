package com.macro.mall.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청 유틸 클래스
 * Created by macro on 2020/10/8.
 */
public class RequestUtil {

    /**
     * 요청의 실제 IP 주소 조회
     * ✏️ 6-4(E09): 요청 헤더(x-forwarded-for·Proxy-Client-IP …)를 그대로 믿지 않는다. 믿는 프록시가 넣은 값만 ClientIp가 고른다.
     */
    public static String getRequestIp(HttpServletRequest request) {
        return ClientIp.of(request);
    }

}
