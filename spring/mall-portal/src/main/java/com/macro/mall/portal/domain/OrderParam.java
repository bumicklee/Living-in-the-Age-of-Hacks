package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 주문 생성 시 전달되는 파라미터
 * Created by macro on 2018/8/30.
 */
@Data
@EqualsAndHashCode
public class OrderParam {
    @Schema(title = "배송지ID")
    private Long memberReceiveAddressId;
    @Schema(title = "쿠폰ID")
    private Long couponId;
    @Schema(title = "사용한 포인트 수")
    private Integer useIntegration;
    @Schema(title = "결제 수단")
    private Integer payType;
    @Schema(title = "선택된 장바구니 상품ID")
    private List<Long> cartIds;
}
