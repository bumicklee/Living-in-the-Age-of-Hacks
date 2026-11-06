package com.macro.mall.security.component;

import org.springframework.security.access.ConfigAttribute;

import java.util.Map;

/**
 * 동적 권한 관련 비즈니스 인터페이스
 * Created by macro on 2020/2/7.
 */
public interface DynamicSecurityService {
    /**
     * 리소스 ANT 와일드카드와 리소스 매핑 로드MAP
     */
    Map<String, ConfigAttribute> loadDataSource();
}
