package com.macro.mall.portal.config;

import com.macro.mall.portal.service.UmsMemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * mall-security모듈 관련 설정
 * Created by macro on 2019/11/5.
 */
@Configuration
public class MallSecurityConfig {

    @Autowired
    private UmsMemberService memberService;

    @Bean
    public UserDetailsService userDetailsService() {
        //로그인 사용자 정보 조회
        return username -> memberService.loadUserByUsername(username);
    }
}
