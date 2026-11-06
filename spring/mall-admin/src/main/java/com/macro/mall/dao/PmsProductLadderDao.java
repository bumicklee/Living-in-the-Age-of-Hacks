package com.macro.mall.dao;

import com.macro.mall.model.PmsProductLadder;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회원 단계별 가격 커스텀Dao
 * Created by macro on 2018/4/26.
 */
public interface PmsProductLadderDao {
    /**
     * 일괄 생성
     */
    int insertList(@Param("list") List<PmsProductLadder> productLadderList);
}
