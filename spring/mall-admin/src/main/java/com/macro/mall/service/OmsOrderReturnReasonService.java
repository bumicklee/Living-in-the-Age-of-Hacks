package com.macro.mall.service;

import com.macro.mall.model.OmsOrderReturnReason;

import java.util.List;

/**
 * 반품 사유 관리Service
 * Created by macro on 2018/10/17.
 */
public interface OmsOrderReturnReasonService {
    /**
     * 반품 사유 추가
     */
    int create(OmsOrderReturnReason returnReason);

    /**
     * 반품 사유 수정
     */
    int update(Long id, OmsOrderReturnReason returnReason);

    /**
     * 반품 사유 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 반품 사유 페이지 조회
     */
    List<OmsOrderReturnReason> list(Integer pageSize, Integer pageNum);

    /**
     * 반품 사유 상태 일괄 수정
     */
    int updateStatus(List<Long> ids, Integer status);

    /**
     * 단일 반품 사유 상세 정보 조회
     */
    OmsOrderReturnReason getItem(Long id);
}
