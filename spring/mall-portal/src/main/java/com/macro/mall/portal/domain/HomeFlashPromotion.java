package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 홈 타임 세일 회차 정보 래퍼
 * Created by macro on 2019/1/28.
 */
@Getter
@Setter
public class HomeFlashPromotion {
    @Schema(title = "이번 회차 시작 시간")
    private Date startTime;
    @Schema(title = "이번 회차 종료 시간")
    private Date endTime;
    @Schema(title = "다음 회차 시작 시간")
    private Date nextStartTime;
    @Schema(title = "다음 회차 종료 시간")
    private Date nextEndTime;
    @Schema(title = "이 타임 세일 행사에 속한 상품")
    private List<FlashPromotionProduct> productList;
}
