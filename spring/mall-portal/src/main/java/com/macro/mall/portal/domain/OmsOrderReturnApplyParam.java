package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 반품 신청 요청 파라미터
 * Created by macro on 2018/10/17.
 */
@Getter
@Setter
public class OmsOrderReturnApplyParam {
    @Schema(title = "주문id")
    private Long orderId;
    @Schema(title = "반품 상품id")
    private Long productId;
    @Schema(title = "주문 번호")
    private String orderSn;
    @Schema(title = "회원 사용자 이름")
    private String memberUsername;
    @Schema(title = "반품인 이름")
    private String returnName;
    @Schema(title = "반품인 전화번호")
    private String returnPhone;
    @Schema(title = "상품 이미지")
    private String productPic;
    @Schema(title = "상품명")
    private String productName;
    @Schema(title = "상품 브랜드")
    private String productBrand;
    @Schema(title = "상품 판매 속성: 색상: 레드; 사이즈:xl;")
    private String productAttr;
    @Schema(title = "반품 수량")
    private Integer productCount;
    @Schema(title = "상품 단가")
    private BigDecimal productPrice;
    @Schema(title = "상품 실결제 단가")
    private BigDecimal productRealPrice;
    @Schema(title = "사유")
    private String reason;
    @Schema(title = "설명")
    private String description;
    @Schema(title = "증빙 이미지, 쉼표로 구분")
    private String proofPics;

}
