package com.macro.mall.portal.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.macro.mall.security.cache.CacheTypeGuard;
import com.macro.mall.common.service.RedisService;
import com.macro.mall.mapper.UmsMemberMapper;
import com.macro.mall.model.UmsMember;
import com.macro.mall.portal.service.UmsMemberCacheService;
import com.macro.mall.security.annotation.CacheException;
import com.macro.mall.security.crypto.FieldCrypto;
import com.macro.mall.security.crypto.PiiMask;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * UmsMemberCacheService구현 클래스
 * Created by macro on 2020/3/14.
 */
@Service
public class UmsMemberCacheServiceImpl implements UmsMemberCacheService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private UmsMemberMapper memberMapper;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.expire.common}")
    private Long REDIS_EXPIRE;
    @Value("${redis.expire.authCode}")
    private Long REDIS_EXPIRE_AUTH_CODE;
    @Value("${redis.key.member}")
    private String REDIS_KEY_MEMBER;
    @Value("${redis.key.authCode}")
    private String REDIS_KEY_AUTH_CODE;

    @Override
    public void delMember(Long memberId) {
        UmsMember umsMember = memberMapper.selectByPrimaryKey(memberId);
        if (umsMember != null) {
            String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + umsMember.getUsername();
            redisService.del(key);
        }
    }

    @Override
    public UmsMember getMember(String username) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + username;
        // 다온마켓 7-미션: 7-7의 형식 고정 읽기를 회원 캐시에도 쓴다
        return CacheTypeGuard.get(redisService, key, "cache:" + REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":*", new TypeReference<UmsMember>() { });
    }

    @Override
    public void setMember(UmsMember member) {
        // 다온마켓 5-3: 캐시에는 전화번호를 가린 값만 둔다(캐시도 평문 개인정보를 두지 않는다). 전화번호가 필요한 곳은 DB에서 다시 읽는다
        UmsMember cached = member;
        if (FieldCrypto.enabled() && member.getPhone() != null) {
            cached = new UmsMember();
            BeanUtils.copyProperties(member, cached);
            cached.setPhone(PiiMask.phone(member.getPhone()));
        }
        String key = REDIS_DATABASE + ":" + REDIS_KEY_MEMBER + ":" + member.getUsername();
        CacheTypeGuard.put(redisService, key, cached, REDIS_EXPIRE);
    }

    @CacheException
    @Override
    public void setAuthCode(String telephone, String authCode) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_AUTH_CODE + ":" + telephone;
        redisService.set(key,authCode,REDIS_EXPIRE_AUTH_CODE);
    }

    @CacheException
    @Override
    public String getAuthCode(String telephone) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_AUTH_CODE + ":" + telephone;
        return (String) redisService.get(key);
    }
}
