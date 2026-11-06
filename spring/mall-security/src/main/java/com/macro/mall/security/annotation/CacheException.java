package com.macro.mall.security.annotation;

import java.lang.annotation.*;

/**
 * 사용자 정의 캐시 예외 어노테이션, 이 어노테이션이 있는 캐시 메서드는 예외를 던짐
 * Created by macro on 2020/3/17.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheException {
}
