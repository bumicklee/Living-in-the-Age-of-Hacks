package com.macro.mall.service;

import com.macro.mall.model.SmsHomeBrand;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 홈 브랜드 관리Service
 * Created by macro on 2018/11/6.
 */
public interface SmsHomeBrandService {
    /**
     * 홈 브랜드 추천 추가
     */
    @Transactional
    int create(List<SmsHomeBrand> homeBrandList);

    /**
     * 브랜드 추천 정렬 수정
     */
    int updateSort(Long id, Integer sort);

    /**
     * 브랜드 추천 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 추천 상태 일괄 업데이트
     */
    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    /**
     * 브랜드 추천 페이지 조회
     */
    List<SmsHomeBrand> list(String brandName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
