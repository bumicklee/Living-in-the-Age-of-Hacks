package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 주문 발송 파라미터
 * Created by macro on 2018/10/12.
 */
@Getter
@Setter
public class OmsOrderDeliveryParam {
    @Schema(title = "주문id")
    private Long orderId;
    @Schema(title = "택배사")
    private String deliveryCompany;
    @Schema(title = "운송장 번호")
    private String deliverySn;
}
