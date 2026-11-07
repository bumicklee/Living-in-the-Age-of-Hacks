package com.macro.mall.daon;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * [교육용 추가] 다온 운영 도구(ops). 관리자 API(8080)와 다른 포트(8095)에서만 열리고, 내 PC(127.0.0.1)에만 공개한다.
 * "내 PC에서만 열리니 괜찮다"고 보고 로그인 없이 쓴다. 수업 2-6(T05)에서 다룬다.
 */
@Configuration
public class DaonOpsConfig {
    @Value("${daon.ops.port:8095}")
    private int opsPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> daonOpsConnector() {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(opsPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    @Bean
    public FilterRegistrationBean<DaonOpsGuardFilter> daonOpsGuard(DaonOpsGuardFilter filter) {
        FilterRegistrationBean<DaonOpsGuardFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);   // Spring Security보다 먼저, 추적 번호 필터(TraceIdConfig)보다는 뒤
        reg.addUrlPatterns("/*");
        return reg;
    }
}
