package com.macro.mall.portal.service;

import com.macro.mall.model.UmsMemberReceiveAddress;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 배송지 관리Service
 * Created by macro on 2018/8/28.
 */
public interface UmsMemberReceiveAddressService {
    /**
     * 배송지 추가
     */
    int add(UmsMemberReceiveAddress address);

    /**
     * 배송지 삭제
     * @param id 주소 테이블의id
     */
    int delete(Long id);

    /**
     * 배송지 수정
     * @param id 주소 테이블의id
     * @param address 수정된 배송지 정보
     */
    @Transactional
    int update(Long id, UmsMemberReceiveAddress address);

    /**
     * 현재 사용자의 배송지 반환
     */
    List<UmsMemberReceiveAddress> list();

    /**
     * 주소 상세 조회
     * @param id 주소id
     */
    UmsMemberReceiveAddress getItem(Long id);
}
