package com.macro.mall.portal.controller;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.model.PmsBrand;
import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.service.PmsPortalBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 홈 브랜드 추천 관리Controller
 * Created by macro on 2020/5/15.
 */
@Controller
@Tag(name = "PmsPortalBrandController", description = "프런트 브랜드 관리")
@RequestMapping("/brand")
public class PmsPortalBrandController {

    @Autowired
    private PmsPortalBrandService portalBrandService;

    @Operation(summary = "추천 브랜드 페이지 조회")
    @RequestMapping(value = "/recommendList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<List<PmsBrand>> recommendList(@RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize,
                                                      @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum) {
        List<PmsBrand> brandList = portalBrandService.recommendList(pageNum, pageSize);
        return CommonResult.success(brandList);
    }

    @Operation(summary = "브랜드 상세 조회")
    @RequestMapping(value = "/detail/{brandId}", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<PmsBrand> detail(@PathVariable Long brandId) {
        PmsBrand brand = portalBrandService.detail(brandId);
        return CommonResult.success(brand);
    }

    @Operation(summary = "브랜드 관련 상품 페이지 조회")
    @RequestMapping(value = "/productList", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<CommonPage<PmsProduct>> productList(@RequestParam Long brandId,
                                                            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                            @RequestParam(value = "pageSize", defaultValue = "6") Integer pageSize) {
        CommonPage<PmsProduct> result = portalBrandService.productList(brandId,pageNum, pageSize);
        return CommonResult.success(result);
    }
}
