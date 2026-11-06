package com.macro.mall.dto;

import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.model.OmsOrderOperateHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 주문 상세 정보
 * Created by macro on 2018/10/11.
 */
public class OmsOrderDetail extends OmsOrder {
    @Getter
    @Setter
    @Schema(title = "주문 상품 목록")
    private List<OmsOrderItem> orderItemList;
    @Getter
    @Setter
    @Schema(title = "주문 작업 기록 목록")
    private List<OmsOrderOperateHistory> historyList;
}
