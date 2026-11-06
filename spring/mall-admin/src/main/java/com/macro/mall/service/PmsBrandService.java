package com.macro.mall.service;

import com.macro.mall.dto.PmsBrandParam;
import com.macro.mall.model.PmsBrand;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 브랜드 관리Service
 * Created by macro on 2018/4/26.
 */
public interface PmsBrandService {
    /**
     * 전체 브랜드 조회
     */
    List<PmsBrand> listAllBrand();

    /**
     * 브랜드 생성
     */
    int createBrand(PmsBrandParam pmsBrandParam);

    /**
     * 브랜드 수정
     */
    @Transactional
    int updateBrand(Long id, PmsBrandParam pmsBrandParam);

    /**
     * 브랜드 삭제
     */
    int deleteBrand(Long id);

    /**
     * 브랜드 일괄 삭제
     */
    int deleteBrand(List<Long> ids);

    /**
     * 브랜드 페이지 조회
     */
    List<PmsBrand> listBrand(String keyword, Integer showStatus, int pageNum, int pageSize);

    /**
     * 브랜드 상세 조회
     */
    PmsBrand getBrand(Long id);

    /**
     * 표시 상태 수정
     */
    int updateShowStatus(List<Long> ids, Integer showStatus);

    /**
     * 제조사 상태 수정
     */
    int updateFactoryStatus(List<Long> ids, Integer factoryStatus);
}
