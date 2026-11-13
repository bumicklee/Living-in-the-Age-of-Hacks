package com.macro.mall.portal.service.impl;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.dao.DaonProductSearchDao;
import com.macro.mall.portal.service.DaonProductSearchService;
import com.macro.mall.security.event.SecurityEventLogger;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [교육용 추가] 다온마켓 상품 검색. 화면에 보여 줄 항목만 골라 돌려준다(재고 등은 내보내지 않는다).
 */
@Service
public class DaonProductSearchServiceImpl implements DaonProductSearchService {
    // 다온마켓 3-3: 정렬은 이름 → 컬럼 매핑. 목록에 없는 이름은 거절한다
    private static final Map<String, String> SORTS = Map.of(
            "new", "id desc", "price_asc", "price asc",
            "price_desc", "price desc", "sale", "sale desc");

    @Autowired
    private DaonProductSearchDao searchDao;

    @Override
    public List<Map<String, Object>> search(String keyword, String sort) {
        String key = sort == null || sort.isBlank() ? "new" : sort;
        String orderBy = SORTS.get(key);
        if (orderBy == null && !SecureKitSwitch.on("sort-allowlist")) {
            // [랩 스위치] secure-kit.sort-allowlist.enabled=false: 고치기 전처럼 받은 값을 그대로 넘긴다(영상용)
            orderBy = sort;
        }
        if (orderBy == null) {
            rejectSort();   // 400 + 탐지 로그
        }
        return searchDao.search(keyword, orderBy).stream().map(DaonProductSearchServiceImpl::item).toList();
    }

    // 다온마켓 3-3 ⑤ 탐지: 정상 화면은 허용 값만 보낸다. 받은 값은 로그에 넣지 않는다(로그 위조·개인정보 섞임 방지)
    private static void rejectSort() {
        SecurityEventLogger.denied("input.rejected.sort_field", "3-3")
                .target("path:/daon/product/search")
                .reason("허용하지 않은 정렬 값")
                .log();
        throw new IllegalArgumentException("허용하지 않은 정렬 값입니다(new·price_asc·price_desc·sale)");
    }

    private static Map<String, Object> item(PmsProduct p) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", p.getId());
        r.put("name", p.getName());
        r.put("subTitle", p.getSubTitle());
        r.put("price", p.getPrice());
        r.put("sale", p.getSale());
        r.put("pic", p.getPic());
        return r;
    }
}
