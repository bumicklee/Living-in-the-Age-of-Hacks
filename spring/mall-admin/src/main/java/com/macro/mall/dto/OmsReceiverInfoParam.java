package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 주문 수령인 정보 수정 파라미터
 * Created by macro on 2018/10/29.
 */
@Getter
@Setter
public class OmsReceiverInfoParam {
    @Schema(title =  "주문ID")
    private Long orderId;
    @Schema(title =  "수령인 이름")
    private String receiverName;
    @Schema(title =  "수령인 전화번호")
    private String receiverPhone;
    @Schema(title =  "수령인 우편번호")
    private String receiverPostCode;
    @Schema(title =  "상세 주소")
    private String receiverDetailAddress;
    @Schema(title =  "시/도, 직할시")
    private String receiverProvince;
    @Schema(title =  "도시")
    private String receiverCity;
    @Schema(title =  "구")
    private String receiverRegion;
    @Schema(title =  "주문 상태:0->결제 대기;1->배송 대기;2->발송됨;3->완료;4->닫힘;5->무효 주문")
    private Integer status;
}
