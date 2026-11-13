package com.macro.mall.security.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.List;

/** [다온마켓 6-6] 보안 헤더 필터와 출처 감시 필터 등록. TraceIdFilter·EndpointProbeFilter 뒤, 보안 필터 앞. */
@Configuration
public class WebHardeningConfig {
    @Bean
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilter() {
        FilterRegistrationBean<SecurityHeadersFilter> reg = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 7);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<OriginWatchFilter> originWatchFilter(@Value("${daon.cors.allowed-origins:}") List<String> allowed) {
        FilterRegistrationBean<OriginWatchFilter> reg = new FilterRegistrationBean<>(new OriginWatchFilter(allowed));
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 8);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
