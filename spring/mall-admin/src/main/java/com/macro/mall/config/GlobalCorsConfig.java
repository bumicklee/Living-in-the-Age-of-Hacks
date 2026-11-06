package com.macro.mall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 전역 교차 출처 설정
 * Created by macro on 2019/7/27.
 */
@Configuration
public class GlobalCorsConfig {

    /**
     * 교차 출처 호출을 허용하는 필터
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        //모든 도메인의 교차 출처 호출 허용
        config.addAllowedOriginPattern("*");
        //교차 출처 전송 허용cookie
        config.setAllowCredentials(true);
        //모든 원본 헤더 정보 통과
        config.addAllowedHeader("*");
        //모든 요청 메서드의 교차 출처 호출 허용
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
