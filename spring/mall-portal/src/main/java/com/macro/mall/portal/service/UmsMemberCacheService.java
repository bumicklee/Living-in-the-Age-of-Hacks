package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMember;

/**
 * 회원 정보 캐시 비즈니스 클래스
 * Created by macro on 2020/3/14.
 */
public interface UmsMemberCacheService {
    /**
     * 회원 사용자 캐시 삭제
     */
    void delMember(Long memberId);

    /**
     * 회원 사용자 캐시 조회
     */
    UmsMember getMember(String username);

    /**
     * 회원 사용자 캐시 설정
     */
    void setMember(UmsMember member);

    /**
     * 인증 코드 설정
     */
    void setAuthCode(String telephone, String authCode);

    /**
     * 인증 코드 발급
     */
    String getAuthCode(String telephone);
}
