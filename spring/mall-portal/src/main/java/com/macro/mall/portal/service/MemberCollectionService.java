package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.MemberProductCollection;
import org.springframework.data.domain.Page;

/**
 * 회원 상품 찜 관리Service
 * Created by macro on 2018/8/2.
 */
public interface MemberCollectionService {
    /**
     * 찜 추가
     */
    int add(MemberProductCollection productCollection);

    /**
     * 찜 삭제
     */
    int delete(Long productId);

    /**
     * 찜 페이지 조회
     */
    Page<MemberProductCollection> list(Integer pageNum, Integer pageSize);

    /**
     * 즐겨찾기 상세 보기
     */
    MemberProductCollection detail(Long productId);

    /**
     * 찜 비우기
     */
    void clear();
}
