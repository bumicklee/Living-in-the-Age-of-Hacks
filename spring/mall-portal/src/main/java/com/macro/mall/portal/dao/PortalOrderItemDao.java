package com.macro.mall.portal.dao;

import com.macro.mall.model.OmsOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 주문 상품 정보 관리 커스텀Dao
 * Created by macro on 2018/9/3.
 */
public interface PortalOrderItemDao {
    /**
     * 일괄 삽입
     */
    int insertList(@Param("list") List<OmsOrderItem> list);
}
