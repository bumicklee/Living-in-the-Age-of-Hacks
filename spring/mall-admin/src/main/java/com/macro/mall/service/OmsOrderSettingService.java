package com.macro.mall.service;

import com.macro.mall.model.OmsOrderSetting;

/**
 * 주문 설정 관리Service
 * Created by macro on 2018/10/16.
 */
public interface OmsOrderSettingService {
    /**
     * 지정한 주문 설정 조회
     */
    OmsOrderSetting getItem(Long id);

    /**
     * 지정한 주문 설정 수정
     */
    int update(Long id, OmsOrderSetting orderSetting);
}
