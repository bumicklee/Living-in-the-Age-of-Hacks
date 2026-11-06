package com.macro.mall.portal.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @auther macrozheng
 * @description 알리페이 요청 클라이언트 설정
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
@Configuration
public class AlipayClientConfig {

    @Bean
    public AlipayClient alipayClient(AlipayConfig config){
        return new DefaultAlipayClient(config.getGatewayUrl(),config.getAppId(),config.getAppPrivateKey(), config.getFormat(),config.getCharset(),config.getAlipayPublicKey(),config.getSignType());
    }
}
