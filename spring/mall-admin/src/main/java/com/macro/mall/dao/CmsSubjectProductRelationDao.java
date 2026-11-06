package com.macro.mall.dao;

import com.macro.mall.model.CmsSubjectProductRelation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 상품-기획전 관계 커스텀Dao
 * Created by macro on 2018/4/26.
 */
public interface CmsSubjectProductRelationDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<CmsSubjectProductRelation> subjectProductRelationList);
}
