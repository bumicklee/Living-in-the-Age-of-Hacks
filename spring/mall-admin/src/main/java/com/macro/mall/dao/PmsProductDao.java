package com.macro.mall.dao;

import com.macro.mall.dto.PmsProductResult;
import org.apache.ibatis.annotations.Param;


/**
 * 상품 관리 커스텀Dao
 * Created by macro on 2018/4/26.
 */
public interface PmsProductDao {
    /**
     * 상품 편집 정보 조회
     */
    PmsProductResult getUpdateInfo(@Param("id") Long id);
}
