package com.macro.mall.portal.domain;

import com.macro.mall.model.PmsProductCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 하위 카테고리를 포함한 상품 카테고리
 * Created by macro on 2020/4/6.
 */
@Getter
@Setter
public class PmsProductCategoryNode extends PmsProductCategory {
    @Schema(title = "하위 카테고리 집합")
    private List<PmsProductCategoryNode> children;
}
