package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.DaonReviewParam;

/**
 * [교육용 추가] 다온마켓 상품 후기(수업 3-4)
 */
public interface DaonReviewService {
    /** 로그인한 회원의 후기를 저장하고 후기 ID를 돌려준다 */
    Long create(DaonReviewParam param);
}
