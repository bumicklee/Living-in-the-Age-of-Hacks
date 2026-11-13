package com.macro.mall.security.config;

import com.macro.mall.security.component.DynamicAuthorizationManager;
import com.macro.mall.security.component.JwtAuthenticationTokenFilter;
import com.macro.mall.security.component.RestAuthenticationEntryPoint;
import com.macro.mall.security.component.RestfulAccessDeniedHandler;
import com.macro.mall.security.securekit.SecureKitSwitch;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


/**
 * SpringSecurity관련 설정, 설정 전용SecurityFilterChain
 * Created by macro on 2019/11/5.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private IgnoreUrlsConfig ignoreUrlsConfig;
    @org.springframework.beans.factory.annotation.Value("${management.server.port:-1}")
    private int managementPort;
    @Autowired
    private RestfulAccessDeniedHandler restfulAccessDeniedHandler;
    @Autowired
    private RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    @Autowired
    private JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter;
    @Autowired(required = false)
    private DynamicAuthorizationManager dynamicAuthorizationManager;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        httpSecurity.authorizeHttpRequests(registry -> {
            // 6-5: 관리 포트(management.server.port)로 들어온 상태 확인만 로그인 없이. 일반 포트의 /actuator/**는 허용 목록에 없다
            if (managementPort > 0) {
                registry.requestMatchers(req -> req.getLocalPort() == managementPort && req.getRequestURI().startsWith("/actuator/health")).permitAll();
            }
            // [다온마켓 9-2 정답, F46] 오류 페이지(/error)는 오류 응답만 만든다. 인증 대상으로 두면 예외가 401(로그인 문제)로 둔갑한다.
            // 오류로 넘어간 요청(ERROR 디스패치)과 /error 주소를 로그인 없이 연다. 응답 본문에 내부 정보가 없는지는 6-5·9-2 처리기가 맡는다
            if (SecureKitSwitch.on("error-path")) {
                registry.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                registry.requestMatchers("/error").permitAll();
            }
            //보호가 필요 없는 리소스 경로는 접근 허용
            for (String url : ignoreUrlsConfig.getUrls()) {
                registry.requestMatchers(url).permitAll();
            }
            //교차 출처 요청의 OPTIONS 요청 허용
            registry.requestMatchers(HttpMethod.OPTIONS).permitAll();
            //모든 요청에 인증 필요
        })
        //모든 요청에 인증 필요
        .authorizeHttpRequests(registry-> registry.anyRequest()
            //동적 권한 설정이 있으면 동적 권한 관리자 추가
            .access(dynamicAuthorizationManager==null? AuthenticatedAuthorizationManager.authenticated():dynamicAuthorizationManager)
        )
        //CSRF 방어 해제
        .csrf(AbstractHttpConfigurer::disable)
        //Session 생성 전략을 무상태 세션으로 변경
        .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        //사용자 정의 권한 거부 처리 클래스
        .exceptionHandling(configurer -> configurer.accessDeniedHandler(restfulAccessDeniedHandler).authenticationEntryPoint(restAuthenticationEntryPoint))
        //사용자 정의 권한 인터셉터 JWT 필터
        .addFilterBefore(jwtAuthenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

}
