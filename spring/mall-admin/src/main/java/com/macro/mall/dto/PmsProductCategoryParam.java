package com.macro.mall.dto;

import com.macro.mall.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 상품 카테고리 파라미터 추가 및 수정
 * Created by macro on 2018/4/26.
 */
@Data
@EqualsAndHashCode
public class PmsProductCategoryParam {
    @Schema(title = "상위 카테고리 번호")
    private Long parentId;
    @NotEmpty
    @Schema(title = "상품 카테고리명",requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(title = "카테고리 단위")
    private String productUnit;
    @FlagValidator(value = {"0","1"},message = "상태는 0 또는1")
    @Schema(title = "내비게이션 바에 표시 여부")
    private Integer navStatus;
    @FlagValidator(value = {"0","1"},message = "상태는 0 또는1")
    @Schema(title = "표시할까요?")
    private Integer showStatus;
    @Min(value = 0)
    @Schema(title = "정렬")
    private Integer sort;
    @Schema(title = "아이콘")
    private String icon;
    @Schema(title = "키워드")
    private String keywords;
    @Schema(title = "설명")
    private String description;
    @Schema(title = "제품 관련 필터 속성 집합")
    private List<Long> productAttributeIdList;
}
