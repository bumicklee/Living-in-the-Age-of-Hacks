package com.macro.mall.dto;

import com.macro.mall.validator.FlagValidator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotEmpty;

/**
 * 상품 속성 파라미터
 * Created by macro on 2018/4/26.
 */
@Data
@EqualsAndHashCode
public class PmsProductAttributeParam {
    @NotEmpty
    @Schema(title = "속성 카테고리ID")
    private Long productAttributeCategoryId;
    @NotEmpty
    @Schema(title = "속성명")
    private String name;
    @FlagValidator({"0","1","2"})
    @Schema(title = "속성 선택 유형:0->고유;1->단일 선택;2->다중 선택")
    private Integer selectType;
    @FlagValidator({"0","1"})
    @Schema(title = "속성 입력 방식:0->직접 입력;1->목록에서 선택")
    private Integer inputType;
    @Schema(title = "선택값 목록, 쉼표로 구분")
    private String inputList;
    private Integer sort;
    @Schema(title = "카테고리 필터 스타일:0->일반;1->색상")
    @FlagValidator({"0","1"})
    private Integer filterType;
    @Schema(title = "검색 유형;0->검색 불필요;1->키워드 검색;2->범위 검색")
    @FlagValidator({"0","1","2"})
    private Integer searchType;
    @Schema(title = "속성이 같은 상품 연관 여부;0->연관 안 함;1->연관")
    @FlagValidator({"0","1"})
    private Integer relatedStatus;
    @Schema(title = "직접 추가 지원 여부;0->지원 안 함;1->지원")
    @FlagValidator({"0","1"})
    private Integer handAddStatus;
    @Schema(title = "속성 유형;0->규격;1->파라미터")
    @FlagValidator({"0","1"})
    private Integer type;
}
