package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonResult;
import com.macro.mall.common.api.IErrorCode;
import com.macro.mall.portal.service.DaonProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * [교육용 추가] 다온마켓 상품 검색(로그인 없이). 검색어와 정렬 이름을 받는다.
 * 원본 상품 검색(/product/search)과 따로 다온마켓이 새로 만든 기능이다. 수업 3-2·3-3(T06)에서 다룬다.
 */
@RestController
@Tag(name = "DaonProductSearchController", description = "다온마켓 상품 검색(교육용 추가)")
@RequestMapping("/daon/product")
public class DaonProductSearchController {
    /** 받을 수 없는 값(400). mall의 CommonResult 모양에 HTTP 상태도 400으로 맞춘다 */
    private static final IErrorCode BAD_REQUEST = new IErrorCode() {
        public long getCode() { return 400; }
        public String getMessage() { return "받을 수 없는 요청 값"; }
    };

    @Autowired
    private DaonProductSearchService searchService;

    @Operation(summary = "상품 검색(검색어, 정렬 이름: new·price_asc·price_desc·sale)")
    @GetMapping("/search")
    public ResponseEntity<CommonResult<List<Map<String, Object>>>> search(@RequestParam(defaultValue = "") String keyword,
                                                                         @RequestParam(required = false) String sort) {
        try {
            return ResponseEntity.ok(CommonResult.success(searchService.search(keyword, sort)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CommonResult.failed(BAD_REQUEST, e.getMessage()));
        }
    }
}
