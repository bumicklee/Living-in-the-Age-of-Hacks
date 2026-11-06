package com.macro.mall.portal.domain;

import com.macro.mall.model.UmsIntegrationConsumeSetting;
import com.macro.mall.model.UmsMemberReceiveAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 확인서 정보 래핑
 * Created by macro on 2018/8/30.
 */
@Getter
@Setter
public class ConfirmOrderResult {
    @Schema(title = "할인 정보를 포함한 장바구니 정보")
    private List<CartPromotionItem> cartPromotionItemList;
    @Schema(title = "사용자 배송지 목록")
    private List<UmsMemberReceiveAddress> memberReceiveAddressList;
    @Schema(title = "사용자 사용 가능 쿠폰 목록")
    private List<SmsCouponHistoryDetail> couponHistoryDetailList;
    @Schema(title = "포인트 사용 규칙")
    private UmsIntegrationConsumeSetting integrationConsumeSetting;
    @Schema(title = "회원 보유 포인트")
    private Integer memberIntegration;
    @Schema(title = "계산된 금액")
    private CalcAmount calcAmount;

    @Getter
    @Setter
    public static class CalcAmount{
        @Schema(title = "주문 상품 총 금액")
        private BigDecimal totalAmount;
        @Schema(title = "배송비")
        private BigDecimal freightAmount;
        @Schema(title = "이벤트 할인")
        private BigDecimal promotionAmount;
        @Schema(title = "결제 예정 금액")
        private BigDecimal payAmount;
    }
}
