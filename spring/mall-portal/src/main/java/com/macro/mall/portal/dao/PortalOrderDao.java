package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrderItem;
import com.macro.mall.portal.domain.OmsOrderDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 프런트 주문 관리 커스텀Dao
 * Created by macro on 2018/9/4.
 */
public interface PortalOrderDao {
    /**
     * 주문 및 주문 상품 상세 조회
     */
    OmsOrderDetail getDetail(@Param("orderId") Long orderId);

    /**
     * pms_sku_stock 테이블의 잠금 재고와 실제 재고 수정
     */
    int updateSkuStock(@Param("itemList") List<OmsOrderItem> orderItemList);

    /**
     * 만료된 주문 조회
     * @param minute 만료 시간(분)
     */
    List<OmsOrderDetail> getTimeOutOrders(@Param("minute") Integer minute);

    /**
     * 주문 상태 일괄 수정
     */
    int updateOrderStatus(@Param("ids") List<Long> ids,@Param("status") Integer status);

    /**
     * 주문 취소 시 재고 잠금 해제
     */
    int releaseSkuStockLock(@Param("itemList") List<OmsOrderItem> orderItemList);

}
