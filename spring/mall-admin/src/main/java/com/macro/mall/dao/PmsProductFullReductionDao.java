package com.macro.mall.dao;

import com.macro.mall.model.PmsProductFullReduction;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 상품 금액 할인 커스텀Dao
 * Created by macro on 2018/4/26.
 */
public interface PmsProductFullReductionDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<PmsProductFullReduction> productFullReductionList);
}
