package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 주문 비용 정보 수정 파라미터
 * Created by macro on 2018/10/29.
 */
@Getter
@Setter
public class OmsMoneyInfoParam {
    @Schema(title = "주문ID")
    private Long orderId;
    @Schema(title = "배송비 금액")
    private BigDecimal freightAmount;
    @Schema(title = "관리자가 관리자 화면에서 주문에 적용한 할인 금액 조정")
    private BigDecimal discountAmount;
    @Schema(title = "주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문")
    private Integer status;
}
