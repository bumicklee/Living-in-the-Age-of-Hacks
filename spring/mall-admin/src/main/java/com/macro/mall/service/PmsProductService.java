package com.macro.mall.service;

import com.macro.mall.dto.PmsProductParam;
import com.macro.mall.dto.PmsProductQueryParam;
import com.macro.mall.dto.PmsProductResult;
import com.macro.mall.model.PmsProduct;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 관리Service
 * Created by macro on 2018/4/26.
 */
public interface PmsProductService {
    /**
     * 상품 생성
     */
    @Transactional(isolation = Isolation.DEFAULT,propagation = Propagation.REQUIRED)
    int create(PmsProductParam productParam);

    /**
     * 상품 ID로 상품 정보 조회(상품 수정용)
     */
    PmsProductResult getUpdateInfo(Long id);

    /**
     * 상품 업데이트
     */
    @Transactional
    int update(Long id, PmsProductParam productParam);

    /**
     * 상품 페이지 조회
     */
    List<PmsProduct> list(PmsProductQueryParam productQueryParam, Integer pageSize, Integer pageNum);

    /**
     * 심사 상태 일괄 수정
     * @param ids 상품 ID 목록
     * @param verifyStatus 심사 상태
     * @param detail 심사 상세
     */
    @Transactional
    int updateVerifyStatus(List<Long> ids, Integer verifyStatus, String detail);

    /**
     * 상품 판매 상태 일괄 수정
     */
    int updatePublishStatus(List<Long> ids, Integer publishStatus);

    /**
     * 상품 추천 상태 일괄 수정
     */
    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    /**
     * 신상품 상태 일괄 수정
     */
    int updateNewStatus(List<Long> ids, Integer newStatus);

    /**
     * 상품 일괄 삭제
     */
    int updateDeleteStatus(List<Long> ids, Integer deleteStatus);

    /**
     * 상품 이름 또는 품번으로 모호 조회
     */
    List<PmsProduct> list(String keyword);
}
