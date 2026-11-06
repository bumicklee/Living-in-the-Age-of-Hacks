package com.macro.mall.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 주문 반품 신청 조회 파라미터
 * Created by macro on 2018/10/18.
 */
@Getter
@Setter
public class OmsReturnApplyQueryParam {
    @Schema(title = "서비스 요청 번호")
    private Long id;
    @Schema(title =  "수령인 이름/번호")
    private String receiverKeyword;
    @Schema(title =  "신청 상태:0->처리 대기;1->반품 중;2->완료;3->거절됨")
    private Integer status;
    @Schema(title =  "신청 시간")
    private String createTime;
    @Schema(title =  "처리 담당자")
    private String handleMan;
    @Schema(title =  "처리 시간")
    private String handleTime;
}
