package com.macro.mall.common.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 요청 유틸 클래스
 * Created by macro on 2020/10/8.
 */
public class RequestUtil {

    /**
     * 요청의 실제 IP 주소 조회
     * ✏️ 6-4(E09): 요청 헤더(x-forwarded-for 등)를 그대로 믿는다. 믿는 프록시가 넣은 값만 쓰도록 ClientIp로 바꾼다.
     */
    public static String getRequestIp(HttpServletRequest request) {
        //HTTP 프록시 서버를 거쳐 전달될 때 추가
        String ipAddress = request.getHeader("x-forwarded-for");
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
            // 로컬에서 접근할 때 네트워크 카드 기준으로 가져온 로컬 설정IP
            if (ipAddress.equals("127.0.0.1") || ipAddress.equals("0:0:0:0:0:0:0:1")) {
                InetAddress inetAddress = null;
                try {
                    inetAddress = InetAddress.getLocalHost();
                } catch (UnknownHostException e) {
                    e.printStackTrace(); // ✏️ 6-5: 화면·표준 출력으로 새는 스택 추적 대신 로거로(원인은 서버 로그에만)
                }
                ipAddress = inetAddress.getHostAddress();
            }
        }
        // 여러 프록시를 거쳐 전달되는 경우 첫 번째 IP가 클라이언트 실제 IP이며, 여러 IP는 다음 순서로','구분
        if (ipAddress != null && ipAddress.length() > 15) {
            if (ipAddress.indexOf(",") > 0) {
                ipAddress = ipAddress.substring(0, ipAddress.indexOf(","));
            }
        }
        return ipAddress;
    }

}
