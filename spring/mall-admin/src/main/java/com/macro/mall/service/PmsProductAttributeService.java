package com.macro.mall.service;

import com.macro.mall.dto.PmsProductAttributeParam;
import com.macro.mall.dto.ProductAttrInfo;
import com.macro.mall.model.PmsProductAttribute;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 속성 관리Service
 * Created by macro on 2018/4/26.
 */
public interface PmsProductAttributeService {
    /**
     * 카테고리 ID와 유형으로 상품 속성 페이징 조회
     * @param cid 카테고리id
     * @param type 0->규격;1->파라미터
     */
    List<PmsProductAttribute> getList(Long cid, Integer type, Integer pageSize, Integer pageNum);

    /**
     * 상품 속성 추가
     */
    @Transactional
    int create(PmsProductAttributeParam pmsProductAttributeParam);

    /**
     * 상품 속성 수정
     */
    int update(Long id, PmsProductAttributeParam productAttributeParam);

    /**
     * 단일 상품 속성 정보 조회
     */
    PmsProductAttribute getItem(Long id);

    /**
     * 상품 속성 일괄 삭제
     */
    @Transactional
    int delete(List<Long> ids);

    /**
     * 상품 카테고리에 해당하는 속성 목록 조회
     */
    List<ProductAttrInfo> getProductAttrInfo(Long productCategoryId);
}
