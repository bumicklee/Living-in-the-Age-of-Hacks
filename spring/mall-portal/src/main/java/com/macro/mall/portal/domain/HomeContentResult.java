package com.macro.mall.portal.domain;

import com.macro.mall.model.CmsSubject;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.model.SmsHomeAdvertise;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 홈 콘텐츠 반환 정보 래퍼
 * Created by macro on 2019/1/28.
 */
@Getter
@Setter
public class HomeContentResult {
    @Schema(title = "배너 광고")
    private List<SmsHomeAdvertise> advertiseList;
    @Schema(title = "추천 브랜드")
    private List<PmsBrand> brandList;
    @Schema(title = "현재 타임 세일 회차")
    private HomeFlashPromotion homeFlashPromotion;
    @Schema(title = "신상품 추천")
    private List<PmsProduct> newProductList;
    @Schema(title = "인기 추천")
    private List<PmsProduct> hotProductList;
    @Schema(title = "추천 기획전")
    private List<CmsSubject> subjectList;
}
