package com.macro.mall.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 상품 카테고리별 속성 정보
 * Created by macro on 2018/5/23.
 */
@Data
@EqualsAndHashCode
public class ProductAttrInfo {
    @Schema(title = "상품 속성ID")
    private Long attributeId;
    @Schema(title = "상품 속성 카테고리ID")
    private Long attributeCategoryId;
}
