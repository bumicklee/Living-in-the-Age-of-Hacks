package com.macro.mall.portal.dao;

import com.macro.mall.model.PmsProduct;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * [교육용 추가] 다온마켓 상품 검색 DAO(resources/dao/DaonProductSearchDao.xml)
 */
public interface DaonProductSearchDao {
    List<PmsProduct> search(@Param("keyword") String keyword, @Param("sort") String sort);
}
