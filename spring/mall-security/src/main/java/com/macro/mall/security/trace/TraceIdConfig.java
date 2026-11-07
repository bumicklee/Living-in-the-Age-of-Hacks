package com.macro.mall.security.trace;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * [다온마켓 추가] 추적 번호 필터를 가장 먼저 돌게 등록한다(mall-admin·mall-portal 둘 다).
 * 다른 필터는 이보다 뒤에 둔다(예: 다온 운영 도구 필터는 HIGHEST_PRECEDENCE + 10).
 */
@Configuration
public class TraceIdConfig {
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter() {
        FilterRegistrationBean<TraceIdFilter> reg = new FilterRegistrationBean<>(new TraceIdFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
