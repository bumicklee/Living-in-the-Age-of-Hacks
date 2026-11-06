package com.macro.mall.dao;

import com.macro.mall.model.PmsSkuStock;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 상품 SKU 관리 커스텀Dao
 * Created by macro on 2018/4/26.
 */
public interface PmsSkuStockDao {
    /**
     * 일괄 삽입 작업
     */
    int insertList(@Param("list")List<PmsSkuStock> skuStockList);

    /**
     * 일괄 삽입 또는 교체 작업
     */
    int replaceList(@Param("list")List<PmsSkuStock> skuStockList);
}
