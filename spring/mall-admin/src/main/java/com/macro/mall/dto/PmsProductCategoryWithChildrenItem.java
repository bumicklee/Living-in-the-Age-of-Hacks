package com.macro.mall.dto;

import com.macro.mall.model.PmsProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 하위 카테고리를 포함한 상품 카테고리
 * Created by macro on 2018/5/25.
 */
public class PmsProductCategoryWithChildrenItem extends PmsProductCategory {
    @Getter
    @Setter
    @Schema(title = "하위 카테고리")
    private List<PmsProductCategory> children;
}
