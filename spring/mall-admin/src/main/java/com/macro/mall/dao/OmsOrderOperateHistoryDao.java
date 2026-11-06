package com.macro.mall.dao;

import com.macro.mall.model.OmsOrderOperateHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 주문 작업 기록 커스텀Dao
 * Created by macro on 2018/10/12.
 */
public interface OmsOrderOperateHistoryDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<OmsOrderOperateHistory> orderOperateHistoryList);
}
