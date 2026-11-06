package com.macro.mall.common.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis작업Service
 * Created by macro on 2020/3/3.
 */
public interface RedisService {

    /**
     * 속성 저장
     */
    void set(String key, Object value, long time);

    /**
     * 속성 저장
     */
    void set(String key, Object value);

    /**
     * 속성 조회
     */
    Object get(String key);

    /**
     * 속성 삭제
     */
    Boolean del(String key);

    /**
     * 속성 일괄 삭제
     */
    Long del(List<String> keys);

    /**
     * 만료 시간 설정
     */
    Boolean expire(String key, long time);

    /**
     * 만료 시간 조회
     */
    Long getExpire(String key);

    /**
     * 해당 속성이 있는지 판단
     */
    Boolean hasKey(String key);

    /**
     * delta만큼 증가
     */
    Long incr(String key, long delta);

    /**
     * delta만큼 감소
     */
    Long decr(String key, long delta);

    /**
     * Hash 구조의 속성 조회
     */
    Object hGet(String key, String hashKey);

    /**
     * Hash 구조에 속성 하나 저장
     */
    Boolean hSet(String key, String hashKey, Object value, long time);

    /**
     * Hash 구조에 속성 하나 저장
     */
    void hSet(String key, String hashKey, Object value);

    /**
     * Hash 구조 전체 조회
     */
    Map<Object, Object> hGetAll(String key);

    /**
     * Hash 구조 전체 설정
     */
    Boolean hSetAll(String key, Map<String, Object> map, long time);

    /**
     * Hash 구조 전체 설정
     */
    void hSetAll(String key, Map<String, ?> map);

    /**
     * Hash 구조에서 속성 삭제
     */
    void hDel(String key, Object... hashKey);

    /**
     * Hash 구조에 해당 속성이 있는지 판단
     */
    Boolean hHasKey(String key, String hashKey);

    /**
     * Hash구조 내 속성 증가
     */
    Long hIncr(String key, String hashKey, Long delta);

    /**
     * Hash구조 내 속성 감소
     */
    Long hDecr(String key, String hashKey, Long delta);

    /**
     * Set 구조 조회
     */
    Set<Object> sMembers(String key);

    /**
     * Set 구조에 속성 추가
     */
    Long sAdd(String key, Object... values);

    /**
     * Set 구조에 속성 추가
     */
    Long sAdd(String key, long time, Object... values);

    /**
     * Set에 포함된 속성인지 여부
     */
    Boolean sIsMember(String key, Object value);

    /**
     * Set 구조의 길이 조회
     */
    Long sSize(String key);

    /**
     * Set 구조에서 속성 삭제
     */
    Long sRemove(String key, Object... values);

    /**
     * List 구조의 속성 조회
     */
    List<Object> lRange(String key, long start, long end);

    /**
     * List 구조의 길이 조회
     */
    Long lSize(String key);

    /**
     * 인덱스로 List의 속성 조회
     */
    Object lIndex(String key, long index);

    /**
     * List 구조에 속성 추가
     */
    Long lPush(String key, Object value);

    /**
     * List 구조에 속성 추가
     */
    Long lPush(String key, Object value, long time);

    /**
     * List 구조에 속성 일괄 추가
     */
    Long lPushAll(String key, Object... values);

    /**
     * List 구조에 속성 일괄 추가
     */
    Long lPushAll(String key, Long time, Object... values);

    /**
     * List 구조에서 속성 제거
     */
    Long lRemove(String key, long count, Object value);
}