package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.DaonReviewParam;
import com.macro.mall.portal.service.DaonReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * [교육용 추가] 다온마켓 상품 후기 쓰기(로그인한 고객). 원본 고객 API에는 후기 쓰기가 없다.
 * 관리자는 mall-admin의 /daon/review/list와 관리자 화면 「상품 후기(교육용)」에서 본다. 수업 3-4·3-5(T07)에서 다룬다.
 */
@RestController
@Tag(name = "DaonReviewController", description = "다온마켓 상품 후기(교육용 추가)")
@RequestMapping("/daon/review")
public class DaonReviewController {
    @Autowired
    private DaonReviewService reviewService;

    @Operation(summary = "상품 후기 쓰기")
    @PostMapping
    public CommonResult<Map<String, Object>> create(@RequestBody DaonReviewParam param) {
        Long id = reviewService.create(param);
        return CommonResult.success(Map.of("id", id));
    }
}
