package com.macro.mall.config;

import com.macro.mall.model.UmsResource;
import com.macro.mall.security.securekit.SecureKitSwitch;
import com.macro.mall.security.component.DynamicSecurityService;
import com.macro.mall.service.UmsAdminService;
import com.macro.mall.service.UmsResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * mall-security모듈 관련 설정
 * Created by macro on 2019/11/9.
 */
@Configuration
public class MallSecurityConfig {

    @Autowired
    private UmsAdminService adminService;
    @Autowired
    private UmsResourceService resourceService;

    @Bean
    public UserDetailsService userDetailsService() {
        //로그인 사용자 정보 조회
        return username -> adminService.loadUserByUsername(username);
    }

    @Bean
    public DynamicSecurityService dynamicSecurityService() {
        return new DynamicSecurityService() {
            @Override
            public Map<String, ConfigAttribute> loadDataSource() {
                Map<String, ConfigAttribute> map = new ConcurrentHashMap<>();
                List<UmsResource> resourceList = resourceService.listAll();
                for (UmsResource resource : resourceList) {
                    map.put(resource.getUrl(), new org.springframework.security.access.SecurityConfig(resource.getId() + ":" + resource.getName()));
                }
                // 다온마켓 2-3: 새 기능도 권한표에 올린다. 고객 정보는 관리자 계정 관리(/admin/**)와 같은 권한이 있어야 본다
                ConfigAttribute adminOnly = map.get("/admin/**");
                if (adminOnly != null && SecureKitSwitch.on("admin-default-deny")) {   // [랩 스위치] 끄면 권한표에 올리지 않는다
                    map.put("/daon/customer/**", adminOnly);
                    map.put("/daon/reconcile/**", adminOnly);   // 다온마켓 10-5: 대사 수동 실행도 같은 권한
                    map.put("/daon/partner/**", adminOnly);     // 다온마켓 12-4: 연동 앱 대장·일괄 폐기도 같은 권한
                    map.put("/daon/backups/**", adminOnly);     // [교육용 추가] 14장 시작 모습: 백업 목록 보기(14-2에서 앱은 백업에 닿지 않게 바꾼다)
                    map.put("/daon/mode/**", adminOnly);        // 다온마켓 14-3: 축소 운영 켜고 끄기(고위험, 13-6 권한 상승도 거친다)
                    map.put("/daon/incident/**", adminOnly);    // 다온마켓 14-4: 사고 대응 격리(고위험, 13-6 권한 상승도 거친다)
                }
                // 다온마켓 13-3: 승인 토큰으로 환불 실행은 반품 신청 관리(/returnApply/**)와 같은 권한
                ConfigAttribute returns = map.get("/returnApply/**");
                if (returns != null) {
                    map.put("/daon/refund/**", returns);
                }
                // [교육용 추가] 다온마켓 상품 기능(상품 후기 목록 3-4, 상품 사진 업로드 3-6)은 상품 관리(/product/**)와 같은 권한으로 연다
                ConfigAttribute goods = map.get("/product/**");
                if (goods != null) {
                    map.put("/daon/review/**", goods);
                    map.put("/minio/**", goods);
                }
                return map;
            }
        };
    }
}
