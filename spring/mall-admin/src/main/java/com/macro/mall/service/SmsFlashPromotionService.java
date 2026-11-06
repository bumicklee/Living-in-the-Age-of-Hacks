package com.macro.mall.service;

import com.macro.mall.model.SmsFlashPromotion;

import java.util.List;

/**
 * 타임 세일 행사 관리Service
 * Created by macro on 2018/11/16.
 */
public interface SmsFlashPromotionService {
    /**
     * 이벤트 추가
     */
    int create(SmsFlashPromotion flashPromotion);

    /**
     * 지정한 이벤트 수정
     */
    int update(Long id, SmsFlashPromotion flashPromotion);

    /**
     * 단일 이벤트 삭제
     */
    int delete(Long id);

    /**
     * 판매/판매 중지 상태 수정
     */
    int updateStatus(Long id, Integer status);

    /**
     * 행사 상세 조회
     */
    SmsFlashPromotion getItem(Long id);

    /**
     * 이벤트 페이지 조회
     */
    List<SmsFlashPromotion> list(String keyword, Integer pageSize, Integer pageNum);
}
