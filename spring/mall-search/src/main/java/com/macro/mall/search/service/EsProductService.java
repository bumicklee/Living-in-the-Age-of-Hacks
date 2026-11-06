package com.macro.mall.search.service;

import com.macro.mall.search.domain.EsProduct;
import com.macro.mall.search.domain.EsProductRelatedInfo;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 검색 상품 관리Service
 * Created by macro on 2018/6/19.
 */
public interface EsProductService {
    /**
     * 데이터베이스의 모든 상품을 다음으로 가져오기ES
     */
    int importAll();

    /**
     * id로 상품 삭제
     */
    void delete(Long id);

    /**
     * id로 상품 생성
     */
    EsProduct create(Long id);

    /**
     * 상품 일괄 삭제
     */
    void delete(List<Long> ids);

    /**
     * 키워드로 이름 또는 부제목 검색하여 상품 조회
     */
    Page<EsProduct> search(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 키워드로 이름 또는 부제목 복합 검색하여 상품 조회
     */
    Page<EsProduct> search(String keyword, Long brandId, Long productCategoryId, Integer pageNum, Integer pageSize,Integer sort);

    /**
     * 상품 id로 관련 상품 추천
     */
    Page<EsProduct> recommend(Long id, Integer pageNum, Integer pageSize);

    /**
     * 키워드 관련 브랜드, 카테고리, 속성 검색
     */
    EsProductRelatedInfo searchRelatedInfo(String keyword);
}
