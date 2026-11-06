package com.macro.mall.service;

import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.model.OmsOrderReturnApply;

import java.util.List;

/**
 * 반품 신청 관리Service
 * Created by macro on 2018/10/18.
 */
public interface OmsOrderReturnApplyService {
    /**
     * 신청 페이지 조회
     */
    List<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum);

    /**
     * 신청 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 지정한 신청 상태 수정
     */
    int updateStatus(Long id, OmsUpdateStatusParam statusParam);

    /**
     * 지정한 신청 상세 조회
     */
    OmsOrderReturnApplyResult getItem(Long id);
}
