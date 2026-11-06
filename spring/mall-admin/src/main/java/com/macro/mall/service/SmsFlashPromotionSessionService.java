package com.macro.mall.service;

import com.macro.mall.dto.SmsFlashPromotionSessionDetail;
import com.macro.mall.model.SmsFlashPromotionSession;

import java.util.List;

/**
 * 타임 세일 회차 관리Service
 * Created by macro on 2018/11/16.
 */
public interface SmsFlashPromotionSessionService {
    /**
     * 회차 추가
     */
    int create(SmsFlashPromotionSession promotionSession);

    /**
     * 회차 수정
     */
    int update(Long id, SmsFlashPromotionSession promotionSession);

    /**
     * 회차 사용 상태 수정
     */
    int updateStatus(Long id, Integer status);

    /**
     * 회차 삭제
     */
    int delete(Long id);

    /**
     * 상세 조회
     */
    SmsFlashPromotionSession getItem(Long id);

    /**
     * 사용 상태로 회차 목록 조회
     */
    List<SmsFlashPromotionSession> list();

    /**
     * 선택 가능한 전체 회차와 수량 조회
     */
    List<SmsFlashPromotionSessionDetail> selectList(Long flashPromotionId);
}
