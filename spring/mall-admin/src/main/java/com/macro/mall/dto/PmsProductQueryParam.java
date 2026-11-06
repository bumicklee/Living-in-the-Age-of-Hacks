package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 상품 조회 파라미터
 * Created by macro on 2018/4/27.
 */
@Data
@EqualsAndHashCode
public class PmsProductQueryParam {
    @Schema(title = "판매 상태")
    private Integer publishStatus;
    @Schema(title = "심사 상태")
    private Integer verifyStatus;
    @Schema(title = "상품명 부분 검색 키워드")
    private String keyword;
    @Schema(title = "상품 코드")
    private String productSn;
    @Schema(title = "상품 카테고리 번호")
    private Long productCategoryId;
    @Schema(title = "상품 브랜드 번호")
    private Long brandId;
}
