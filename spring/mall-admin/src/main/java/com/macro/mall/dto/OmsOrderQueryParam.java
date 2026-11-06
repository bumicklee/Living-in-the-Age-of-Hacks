package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 주문 조회 파라미터
 * Created by macro on 2018/10/11.
 */
@Getter
@Setter
public class OmsOrderQueryParam {
    @Schema(title =  "주문 번호")
    private String orderSn;
    @Schema(title =  "수령인 이름/번호")
    private String receiverKeyword;
    @Schema(title =  "주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문")
    private Integer status;
    @Schema(title =  "주문 유형:0->정상 주문;1->타임 세일 주문")
    private Integer orderType;
    @Schema(title =  "주문 출처:0->PC주문;1->app주문")
    private Integer sourceType;
    @Schema(title =  "주문 제출 시간")
    private String createTime;
}
