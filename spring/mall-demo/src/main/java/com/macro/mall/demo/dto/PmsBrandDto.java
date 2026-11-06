package com.macro.mall.demo.dto;

import com.macro.mall.demo.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 브랜드 전달 파라미터
 * Created by macro on 2019/4/8.
 */
@Schema(title = "PmsBrandDto")
public class PmsBrandDto {
    @Schema(title = "브랜드명",requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "이름은 비워둘 수 없습니다")
    private String name;
    @Schema(title = "브랜드 첫 글자",requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "초성은 비워 둘 수 없습니다")
    private String firstLetter;
    @Schema(title = "정렬 필드")
    @Min(value = 0, message = "정렬 최솟값은0")
    private Integer sort;
    @Schema(title = "제조사 여부")
    @FlagValidator(value = {"0","1"}, message = "제조사 상태가 올바르지 않습니다")
    private Integer factoryStatus;
    @Schema(title = "표시할까요?")
    @FlagValidator(value = {"0","1"}, message = "표시 상태가 올바르지 않습니다")
    private Integer showStatus;
    @Schema(title = "브랜드logo")
    private String logo;
    @Schema(title = "브랜드 대형 이미지")
    private String bigPic;
    @Schema(title = "브랜드 스토리")
    private String brandStory;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFirstLetter() {
        return firstLetter;
    }

    public void setFirstLetter(String firstLetter) {
        this.firstLetter = firstLetter;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getFactoryStatus() {
        return factoryStatus;
    }

    public void setFactoryStatus(Integer factoryStatus) {
        this.factoryStatus = factoryStatus;
    }

    public Integer getShowStatus() {
        return showStatus;
    }

    public void setShowStatus(Integer showStatus) {
        this.showStatus = showStatus;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getBigPic() {
        return bigPic;
    }

    public void setBigPic(String bigPic) {
        this.bigPic = bigPic;
    }

    public String getBrandStory() {
        return brandStory;
    }

    public void setBrandStory(String brandStory) {
        this.brandStory = brandStory;
    }
}
