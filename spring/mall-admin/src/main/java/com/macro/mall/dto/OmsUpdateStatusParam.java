package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 수취 확인 요청 파라미터
 * Created by macro on 2018/10/18.
 */
@Getter
@Setter
public class OmsUpdateStatusParam {
    @Schema(title = "서비스 요청 번호")
    private Long id;
    @Schema(title = "배송지 연관id")
    private Long companyAddressId;
    @Schema(title = "환불 금액 확인")
    private BigDecimal returnAmount;
    @Schema(title = "메모 처리")
    private String handleNote;
    @Schema(title = "처리자")
    private String handleMan;
    @Schema(title = "배송 메모")
    private String receiveNote;
    @Schema(title = "수령인")
    private String receiveMan;
    @Schema(title = "신청 상태:1->반품 중;2->완료;3->거절됨")
    private Integer status;
}
