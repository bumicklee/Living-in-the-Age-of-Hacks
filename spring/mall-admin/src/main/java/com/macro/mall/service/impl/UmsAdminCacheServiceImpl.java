package com.macro.mall.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.macro.mall.common.service.RedisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.macro.mall.security.cache.CacheTypeGuard;
import com.macro.mall.security.crypto.CacheHmac;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.dao.UmsAdminRoleRelationDao;
import com.macro.mall.mapper.UmsAdminRoleRelationMapper;
import com.macro.mall.model.UmsAdmin;
import com.macro.mall.model.UmsAdminRoleRelation;
import com.macro.mall.model.UmsAdminRoleRelationExample;
import com.macro.mall.model.UmsResource;
import com.macro.mall.service.UmsAdminCacheService;
import com.macro.mall.service.UmsAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 사용자 캐시 관리 Service 구현 클래스
 * Created by macro on 2020/3/13.
 */
@Service
public class UmsAdminCacheServiceImpl implements UmsAdminCacheService {
    @Autowired
    private UmsAdminService adminService;
    @Autowired
    private RedisService redisService;
    @Autowired
    private CacheHmac cacheHmac;
    @Autowired
    private UmsAdminRoleRelationMapper adminRoleRelationMapper;
    @Autowired
    private UmsAdminRoleRelationDao adminRoleRelationDao;
    @Value("${redis.database}")
    private String REDIS_DATABASE;
    @Value("${redis.expire.common}")
    private Long REDIS_EXPIRE;
    @Value("${redis.key.admin}")
    private String REDIS_KEY_ADMIN;
    @Value("${redis.key.resourceList}")
    private String REDIS_KEY_RESOURCE_LIST;

    @Override
    public void delAdmin(Long adminId) {
        UmsAdmin admin = adminService.getItem(adminId);
        if (admin != null) {
            String key = REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":" + admin.getUsername();
            redisService.del(key);
        }
    }

    @Override
    public void delResourceList(Long adminId) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":" + adminId;
        redisService.del(key);
    }

    @Override
    public void delResourceListByRole(Long roleId) {
        UmsAdminRoleRelationExample example = new UmsAdminRoleRelationExample();
        example.createCriteria().andRoleIdEqualTo(roleId);
        List<UmsAdminRoleRelation> relationList = adminRoleRelationMapper.selectByExample(example);
        if (CollUtil.isNotEmpty(relationList)) {
            String keyPrefix = REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":";
            List<String> keys = relationList.stream().map(relation -> keyPrefix + relation.getAdminId()).collect(Collectors.toList());
            redisService.del(keys);
        }
    }

    @Override
    public void delResourceListByRoleIds(List<Long> roleIds) {
        UmsAdminRoleRelationExample example = new UmsAdminRoleRelationExample();
        example.createCriteria().andRoleIdIn(roleIds);
        List<UmsAdminRoleRelation> relationList = adminRoleRelationMapper.selectByExample(example);
        if (CollUtil.isNotEmpty(relationList)) {
            String keyPrefix = REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":";
            List<String> keys = relationList.stream().map(relation -> keyPrefix + relation.getAdminId()).collect(Collectors.toList());
            redisService.del(keys);
        }
    }

    @Override
    public void delResourceListByResource(Long resourceId) {
        List<Long> adminIdList = adminRoleRelationDao.getAdminIdList(resourceId);
        if (CollUtil.isNotEmpty(adminIdList)) {
            String keyPrefix = REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":";
            List<String> keys = adminIdList.stream().map(adminId -> keyPrefix + adminId).collect(Collectors.toList());
            redisService.del(keys);
        }
    }

    @Override
    public UmsAdmin getAdmin(String username) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":" + username;
        // 다온마켓 7-7: 형식을 고정해서 읽는다(캐시의 글자가 만들 타입을 고르지 못한다)
        return CacheTypeGuard.get(redisService, key, "cache:" + REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":*", new TypeReference<UmsAdmin>() { });
    }

    @Override
    public void setAdmin(UmsAdmin admin) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_ADMIN + ":" + admin.getUsername();
        CacheTypeGuard.put(redisService, key, admin, REDIS_EXPIRE);
    }

    @Override
    public List<UmsResource> getResourceList(Long adminId) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":" + adminId;
        // 다온마켓 7-미션: 권한 목록 캐시도 형식을 고정해서 읽는다(서명 확인은 그대로)
        List<UmsResource> cached = CacheTypeGuard.get(redisService, key, "cache:" + REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":*", new TypeReference<List<UmsResource>>() { });
        if (cached == null || !SecureKitSwitch.on("cache-hmac")) {
            return cached;
        }
        Object sig;
        try {
            sig = redisService.get(key + ":sig");
        } catch (RuntimeException unreadable) {
            sig = null;            // 읽을 수 없는 서명도 없는 서명과 같다
        }
        if (sig == null || !cacheHmac.verify(canonical(adminId, cached), String.valueOf(sig))) {
            // 6-4: 서명이 없거나 맞지 않는 캐시는 버리고 DB에서 다시 읽게 한다(null이면 서비스가 DB를 본다)
            SecurityEventLogger.denied("cache.integrity.failed", "6-4")
                    .target("cache:resourceList:" + adminId)
                    .reason("Redis 권한 캐시의 서명 불일치, DB에서 다시 읽음")
                    .log();
            redisService.del(key);
            return null;
        }
        return cached;
    }

    @Override
    public void setResourceList(Long adminId, List<UmsResource> resourceList) {
        String key = REDIS_DATABASE + ":" + REDIS_KEY_RESOURCE_LIST + ":" + adminId;
        CacheTypeGuard.put(redisService, key, resourceList, REDIS_EXPIRE);
        if (SecureKitSwitch.on("cache-hmac")) {
            redisService.set(key + ":sig", cacheHmac.sign(canonical(adminId, resourceList)), REDIS_EXPIRE);
        }
    }

    /** 서명 대상: 관리자 번호 + 정렬한 권한 번호(·주소) 목록. 순서와 직렬화 방식에 흔들리지 않는다 */
    private static String canonical(Long adminId, List<UmsResource> list) {
        return adminId + "|" + list.stream().map(r -> r.getId() + ":" + r.getUrl()).sorted().collect(Collectors.joining(","));
    }
}
