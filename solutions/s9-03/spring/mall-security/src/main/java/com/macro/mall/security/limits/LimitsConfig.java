package com.macro.mall.security.limits;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * [다온마켓 9-3 정답] 입력 상한 필터 두 개를 보안 필터보다 앞에 둔다(로그인 전 큰 본문도 끝까지 읽지 않게).
 * TraceIdFilter(맨 앞)·EndpointProbeFilter(+5) 다음.
 */
@Configuration
public class LimitsConfig {
    @Bean
    public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter() {
        FilterRegistrationBean<RequestSizeLimitFilter> reg = new FilterRegistrationBean<>(new RequestSizeLimitFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<PageSizeLimitFilter> pageSizeLimitFilter() {
        FilterRegistrationBean<PageSizeLimitFilter> reg = new FilterRegistrationBean<>(new PageSizeLimitFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
