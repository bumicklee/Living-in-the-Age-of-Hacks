package com.macro.mall.portal.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * [교육용 추가] 다온마켓 상품 후기 쓰기 요청(수업 3-4)
 */
@Getter
@Setter
public class DaonReviewParam {
    /** 상품 ID */
    private Long productId;
    /** 별점 1~5 */
    private Integer star;
    /** 후기 본문(서식 없는 글자) */
    private String content;
}
