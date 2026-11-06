package com.macro.mall.service;

import com.macro.mall.model.SmsHomeAdvertise;

import java.util.List;

/**
 * 홈 광고 관리Service
 * Created by macro on 2018/11/7.
 */
public interface SmsHomeAdvertiseService {
    /**
     * 광고 추가
     */
    int create(SmsHomeAdvertise advertise);

    /**
     * 광고 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 판매/판매 중지 상태 수정
     */
    int updateStatus(Long id, Integer status);

    /**
     * 광고 상세 조회
     */
    SmsHomeAdvertise getItem(Long id);

    /**
     * 광고 업데이트
     */
    int update(Long id, SmsHomeAdvertise advertise);

    /**
     * 광고 페이지 조회
     */
    List<SmsHomeAdvertise> list(String name, Integer type, String endTime, Integer pageSize, Integer pageNum);
}
