package com.macro.mall.portal.service;

import com.macro.mall.common.api.CommonPage;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.domain.ConfirmOrderResult;
import com.macro.mall.portal.domain.OmsOrderDetail;
import com.macro.mall.portal.domain.OrderParam;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 프런트 주문 관리Service
 * Created by macro on 2018/8/30.
 */
public interface OmsPortalOrderService {
    /**
     * 사용자 장바구니 정보로 주문 확인서 정보 생성
     */
    ConfirmOrderResult generateConfirmOrder(List<Long> cartIds);

    /**
     * 제출 정보로 주문 생성
     */
    @Transactional
    Map<String, Object> generateOrder(OrderParam orderParam);

    /**
     * 결제 성공 후 콜백
     */
    @Transactional
    Integer paySuccess(Long orderId, Integer payType);

    /**
     * 만료된 주문 자동 취소
     */
    @Transactional
    Integer cancelTimeOutOrder();

    /**
     * 기한 초과 주문 단건 취소
     */
    @Transactional
    void cancelOrder(Long orderId);

    /**
     * 지연 메시지를 전송해 주문 취소
     */
    void sendDelayMessageCancelOrder(Long orderId);

    /**
     * 수취 확인
     */
    void confirmReceiveOrder(Long orderId);

    /**
     * 사용자 주문 페이지 조회
     */
    CommonPage<OmsOrderDetail> list(Integer status, Integer pageNum, Integer pageSize);

    /**
     * 주문 ID로 주문 상세 조회
     */
    OmsOrderDetail detail(Long orderId);

    /**
     * 사용자가 주문 ID로 주문 삭제
     */
    void deleteOrder(Long orderId);

    /**
     * orderSn 기준 결제 성공 처리 로직
     */
    @Transactional
    void paySuccessByOrderSn(String orderSn, Integer payType);

    /**
     * orderSn으로 주문 조회
     */
    OmsOrder getOrderByOrderSn(String orderSn);
}
