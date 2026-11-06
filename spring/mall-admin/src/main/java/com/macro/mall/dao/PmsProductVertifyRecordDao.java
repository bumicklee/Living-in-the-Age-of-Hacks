package com.macro.mall.dao;

import com.macro.mall.model.PmsProductVertifyRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 상품 심사 로그 관리 커스텀Dao
 * Created by macro on 2018/4/27.
 */
public interface PmsProductVertifyRecordDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<PmsProductVertifyRecord> list);
}
