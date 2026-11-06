package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MemberReadHistory;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 회원 조회 기록 관리Service
 * Created by macro on 2018/8/3.
 */
public interface MemberReadHistoryService {
    /**
     * 조회 기록 생성
     */
    int create(MemberReadHistory memberReadHistory);

    /**
     * 조회 기록 일괄 삭제
     */
    int delete(List<String> ids);

    /**
     * 사용자 조회 내역 페이지 조회
     */
    Page<MemberReadHistory> list(Integer pageNum, Integer pageSize);

    /**
     * 조회 기록 비우기
     */
    void clear();
}
