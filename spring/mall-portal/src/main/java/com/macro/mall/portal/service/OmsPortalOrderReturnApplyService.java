package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.OmsOrderReturnApplyParam;

/**
 * 프런트 주문 반품 관리Service
 * Created by macro on 2018/10/17.
 */
public interface OmsPortalOrderReturnApplyService {
    /**
     * 신청 제출
     */
    int create(OmsOrderReturnApplyParam returnApply);
}
