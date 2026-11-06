package com.macro.mall.dto;

import com.macro.mall.model.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 상품 생성·수정 요청 파라미터
 * Created by macro on 2018/4/26.
 */
@Data
@EqualsAndHashCode
public class PmsProductParam extends PmsProduct{
    @Schema(title = "상품 수량별 가격 설정")
    private List<PmsProductLadder> productLadderList;
    @Schema(title = "상품 금액 할인 가격 설정")
    private List<PmsProductFullReduction> productFullReductionList;
    @Schema(title = "상품 회원 가격 설정")
    private List<PmsMemberPrice> memberPriceList;
    @Schema(title = "상품의 sku 재고 정보")
    private List<PmsSkuStock> skuStockList;
    @Schema(title = "상품 파라미터 및 사용자 정의 규격 속성")
    private List<PmsProductAttributeValue> productAttributeValueList;
    @Schema(title = "기획전과 상품 관계")
    private List<CmsSubjectProductRelation> subjectProductRelationList;
    @Schema(title = "엄선 전용관과 상품의 관계")
    private List<CmsPrefrenceAreaProductRelation> prefrenceAreaProductRelationList;
}
