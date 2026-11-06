package com.macro.mall.portal.repository;

import com.macro.mall.portal.domain.MemberProductCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * 회원 상품 찜Repository
 * Created by macro on 2018/8/2.
 */
public interface MemberProductCollectionRepository extends MongoRepository<MemberProductCollection, String> {
    /**
     * 회원 ID와 상품 ID로 기록 조회
     */
    MemberProductCollection findByMemberIdAndProductId(Long memberId, Long productId);

    /**
     * 회원 ID와 상품 ID로 기록 삭제
     */
    int deleteByMemberIdAndProductId(Long memberId, Long productId);

    /**
     * 회원 ID로 기록 페이징 조회
     */
    Page<MemberProductCollection> findByMemberId(Long memberId, Pageable pageable);

    /**
     * 회원 ID로 기록 삭제
     */
    void deleteAllByMemberId(Long memberId);
}
