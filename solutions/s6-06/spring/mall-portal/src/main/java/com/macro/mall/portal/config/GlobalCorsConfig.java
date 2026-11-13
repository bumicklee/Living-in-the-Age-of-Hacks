package com.macro.mall.portal.config;

import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 전역 교차 출처 관련 설정
 * Created by macro on 2019/7/27.
 */
@Configuration
public class GlobalCorsConfig {

    /** 허용 출처 목록(application.yml의 daon.cors.allowed-origins, 쉼표로 구분) */
    @Value("${daon.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    /**
     * 교차 출처 호출을 허용하는 필터
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        if (SecureKitSwitch.on("cors-allowlist")) {
            // 6-6: 정확히 같은 출처만(포함 검사가 아니다). 자격증명 허용도 이 목록에만. 메서드·헤더도 쓰는 것만
            config.setAllowedOrigins(allowedOrigins);
            config.setAllowCredentials(true);
            config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setMaxAge(600L);
        } else {
            config.addAllowedOriginPattern("*");
            config.setAllowCredentials(true);
            config.addAllowedHeader("*");
            config.addAllowedMethod("*");
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
