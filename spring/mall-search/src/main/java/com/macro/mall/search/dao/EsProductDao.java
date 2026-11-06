package com.macro.mall.search.dao;

import com.macro.mall.search.domain.EsProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 검색 상품 관리 커스텀Dao
 * Created by macro on 2018/6/19.
 */
public interface EsProductDao {
    /**
     * 지정한 ID의 검색 상품 조회
     */
    List<EsProduct> getAllEsProductList(@Param("id") Long id);
}
