package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MemberBrandAttention;
import org.springframework.data.domain.Page;

/**
 * 회원 브랜드 팔로우 관리Service
 * Created by macro on 2018/8/2.
 */
public interface MemberAttentionService {
    /**
     * 팔로우 추가
     */
    int add(MemberBrandAttention memberBrandAttention);

    /**
     * 관심 해제
     */
    int delete(Long brandId);

    /**
     * 사용자 관심 목록 조회
     */
    Page<MemberBrandAttention> list(Integer pageNum, Integer pageSize);

    /**
     * 사용자 관심 상세 조회
     */
    MemberBrandAttention detail(Long brandId);

    /**
     * 팔로우 목록 비우기
     */
    void clear();
}
