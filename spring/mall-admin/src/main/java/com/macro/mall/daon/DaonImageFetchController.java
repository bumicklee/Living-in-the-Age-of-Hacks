package com.macro.mall.daon;

import com.macro.mall.common.api.CommonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [교육용 추가] 상품 등록 화면의 「이미지 URL로 가져오기」.
 * 관리자가 붙여 넣은 외부 이미지 주소를 서버가 대신 받아 미리 보기로 돌려준다. 수업 2-5(T04)에서 다룬다.
 * 실제로 주소를 부르는 코드는 DaonImageFetcher에 있다.
 */
@RestController
@Tag(name = "DaonImageFetchController", description = "상품 이미지 URL 가져오기(교육용 추가)")
@RequestMapping("/product/image")
public class DaonImageFetchController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DaonImageFetchController.class);
    @Autowired
    private DaonImageFetcher fetcher;

    @Operation(summary = "이미지 URL 가져오기(미리 보기)")
    @GetMapping("/fetch")
    public ResponseEntity<?> fetch(@RequestParam String url) {
        try {
            DaonImageFetcher.Fetched f = fetcher.fetch(url);
            MediaType type = f.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(f.contentType());
            return ResponseEntity.ok().contentType(type).body(f.body());
        } catch (Exception e) {
            LOGGER.warn("이미지 가져오기 실패 url={} 이유={}", url, e.getMessage());
            return ResponseEntity.ok(CommonResult.failed("이미지를 가져오지 못했습니다: " + e.getMessage()));
        }
    }
}
