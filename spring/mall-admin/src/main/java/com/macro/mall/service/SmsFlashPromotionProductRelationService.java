package com.macro.mall.service;

import com.macro.mall.dto.SmsFlashPromotionProduct;
import com.macro.mall.model.SmsFlashPromotionProductRelation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 타임 세일 상품 연관 관리Service
 * Created by macro on 2018/11/16.
 */
public interface SmsFlashPromotionProductRelationService {
    /**
     * 연관 상품 일괄 추가
     */
    @Transactional
    int create(List<SmsFlashPromotionProductRelation> relationList);

    /**
     * 연관 정보 수정
     */
    int update(Long id, SmsFlashPromotionProductRelation relation);

    /**
     * 연관 삭제
     */
    int delete(Long id);

    /**
     * 연관 상세 조회
     */
    SmsFlashPromotionProductRelation getItem(Long id);

    /**
     * 관련 상품 및 타임 세일 프로모션 정보 페이지 조회
     *
     * @param flashPromotionId        타임 세일id
     * @param flashPromotionSessionId 타임 세일 회차id
     */
    List<SmsFlashPromotionProduct> list(Long flashPromotionId, Long flashPromotionSessionId, Integer pageSize, Integer pageNum);

    /**
     * 이벤트와 회차 id로 상품 관계 수 조회
     * @param flashPromotionId        타임 세일id
     * @param flashPromotionSessionId 타임 세일 회차id
     */
    long getCount(Long flashPromotionId,Long flashPromotionSessionId);
}
