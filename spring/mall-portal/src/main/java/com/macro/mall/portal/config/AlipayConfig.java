package com.macro.mall.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @auther macrozheng
 * @description 알리페이 결제 관련 설정
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {
    /**
     * 알리페이 게이트웨이
     */
    private String gatewayUrl;
    /**
     * 애플리케이션ID
     */
    private String appId;
    /**
     * 애플리케이션 개인 키
     */
    private String appPrivateKey;
    /**
     * 알리페이 공개 키
     */
    private String alipayPublicKey;
    /**
     * 사용자가 결제를 확인한 뒤 알리페이가 호출하는 페이지 반환 경로
     * 개발 환경은 다음과 같습니다:http://localhost:8060/#/pages/money/paySuccess
     */
    private String returnUrl;
    /**
     * 결제 성공 후 알리페이 서버가 가맹점 서버로 보내는 비동기 알림 콜백(외부에서 접근 가능해야 함)
     * 개발 환경은 다음과 같습니다:http://localhost:8085/alipay/notify
     */
    private String notifyUrl;
    /**
     * 파라미터 반환 형식, 지원 형식은JSON
     */
    private String format = "JSON";
    /**
     * 요청에 사용하는 인코딩 형식
     */
    private String charset = "UTF-8";
    /**
     * 서명 문자열 생성에 사용하는 서명 알고리즘 유형
     */
    private String signType = "RSA2";
}
