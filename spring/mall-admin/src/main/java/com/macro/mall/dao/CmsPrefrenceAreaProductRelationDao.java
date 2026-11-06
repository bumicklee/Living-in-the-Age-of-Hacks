package com.macro.mall.dao;

import com.macro.mall.model.CmsPrefrenceAreaProductRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 엄선과 상품 관계 커스텀Dao
 * Created by macro on 2018/4/26.
 */
public interface CmsPrefrenceAreaProductRelationDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<CmsPrefrenceAreaProductRelation> prefrenceAreaProductRelationList);
}
