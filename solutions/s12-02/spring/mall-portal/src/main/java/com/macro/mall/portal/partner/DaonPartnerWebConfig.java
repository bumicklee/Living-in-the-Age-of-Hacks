package com.macro.mall.portal.partner;

import com.macro.mall.security.securekit.PartnerScopeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * [다온마켓 12-2 정답] 파트너 경로 문지기(secure-kit PartnerScopeInterceptor)를 /partner/** 에만 건다.
 * 고객 경로는 지금처럼 고객 토큰 필터(JwtAuthenticationTokenFilter)가 지킨다. 두 문은 서로의 토큰을 받지 않는다(aud).
 */
@Configuration
public class DaonPartnerWebConfig implements WebMvcConfigurer {
    @Autowired
    private PartnerScopeInterceptor partnerScope;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(partnerScope).addPathPatterns("/partner/**");
    }
}
