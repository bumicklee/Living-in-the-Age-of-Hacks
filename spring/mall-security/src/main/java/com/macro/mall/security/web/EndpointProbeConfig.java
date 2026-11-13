package com.macro.mall.security.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** [다온마켓 6-5] 관리·문서 경로 탐지 필터를 보안 필터보다 앞에 등록한다(막힌 요청도 기록하려고). TraceIdFilter 바로 뒤. */
@Configuration
public class EndpointProbeConfig {
    @Bean
    public FilterRegistrationBean<EndpointProbeFilter> endpointProbeFilter() {
        FilterRegistrationBean<EndpointProbeFilter> reg = new FilterRegistrationBean<>(new EndpointProbeFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
