package com.macro.mall.dao;

import com.macro.mall.dto.OmsOrderDeliveryParam;
import com.macro.mall.dto.OmsOrderDetail;
import com.macro.mall.dto.OmsOrderQueryParam;
import com.macro.mall.model.OmsOrder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 주문 조회 커스텀Dao
 * Created by macro on 2018/10/12.
 */
public interface OmsOrderDao {
    /**
     * 조건으로 주문 조회
     */
    List<OmsOrder> getList(@Param("queryParam") OmsOrderQueryParam queryParam);

    /**
     * 일괄 발송
     */
    int delivery(@Param("list") List<OmsOrderDeliveryParam> deliveryParamList);

    /**
     * 주문 상세 조회
     */
    OmsOrderDetail getDetail(@Param("id") Long id);
}
