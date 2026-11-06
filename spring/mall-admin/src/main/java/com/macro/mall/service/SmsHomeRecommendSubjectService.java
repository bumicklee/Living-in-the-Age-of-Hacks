package com.macro.mall.service;

import com.macro.mall.model.SmsHomeRecommendSubject;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 홈 기획전 추천 관리Service
 * Created by macro on 2018/11/7.
 */
public interface SmsHomeRecommendSubjectService {
    /**
     * 홈 추천 추가
     */
    @Transactional
    int create(List<SmsHomeRecommendSubject> recommendSubjectList);

    /**
     * 추천 정렬 수정
     */
    int updateSort(Long id, Integer sort);

    /**
     * 추천 일괄 삭제
     */
    int delete(List<Long> ids);

    /**
     * 추천 상태 일괄 업데이트
     */
    int updateRecommendStatus(List<Long> ids, Integer recommendStatus);

    /**
     * 추천 페이지 조회
     */
    List<SmsHomeRecommendSubject> list(String subjectName, Integer recommendStatus, Integer pageSize, Integer pageNum);
}
