package com.macro.mall.service;

import com.macro.mall.dto.*;
import com.macro.mall.model.OmsOrder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 관리Service
 * Created by macro on 2018/10/11.
 */
public interface OmsOrderService {
    /**
     * 주문 페이지 조회
     */
    List<OmsOrder> list(OmsOrderQueryParam queryParam, Integer pageSize, Integer pageNum);

    /**
     * 일괄 발송
     */
    @Transactional
    int delivery(List<OmsOrderDeliveryParam> deliveryParamList);

    /**
     * 주문 일괄 닫기
     */
    @Transactional
    int close(List<Long> ids, String note);

    /**
     * 주문 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 지정한 주문 상세 조회
     */
    OmsOrderDetail detail(Long id);

    /**
     * 주문 수령인 정보 수정
     */
    @Transactional
    int updateReceiverInfo(OmsReceiverInfoParam receiverInfoParam);

    /**
     * 주문 비용 정보 수정
     */
    @Transactional
    int updateMoneyInfo(OmsMoneyInfoParam moneyInfoParam);

    /**
     * 주문 비고 수정
     */
    @Transactional
    int updateNote(Long id, String note, Integer status);
}
