package com.macro.mall.dto;

import com.macro.mall.model.UmsMenu;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 관리자 메뉴 노드 래핑
 * Created by macro on 2020/2/4.
 */
@Getter
@Setter
public class UmsMenuNode extends UmsMenu {
    @Schema(title =  "하위 메뉴")
    private List<UmsMenuNode> children;
}
