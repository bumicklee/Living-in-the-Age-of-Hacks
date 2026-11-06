package com.macro.mall.dto;

import com.macro.mall.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

/**
 * 브랜드 요청 파라미터
 * Created by macro on 2018/4/26.
 */
@Data
@EqualsAndHashCode
public class PmsBrandParam {
    @NotEmpty
    @Schema(title =  "브랜드명",requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(title =  "브랜드 첫 글자")
    private String firstLetter;
    @Min(value = 0)
    @Schema(title =  "정렬 필드")
    private Integer sort;
    @FlagValidator(value = {"0","1"}, message = "제조사 상태가 올바르지 않습니다")
    @Schema(title =  "제조사 여부")
    private Integer factoryStatus;
    @FlagValidator(value = {"0","1"}, message = "표시 상태가 올바르지 않습니다")
    @Schema(title =  "표시할까요?")
    private Integer showStatus;
    @NotEmpty
    @Schema(title =  "브랜드logo",requiredMode = Schema.RequiredMode.REQUIRED)
    private String logo;
    @Schema(title =  "브랜드 대형 이미지")
    private String bigPic;
    @Schema(title =  "브랜드 스토리")
    private String brandStory;
}
