package com.macro.mall.portal.service.impl;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.portal.dao.DaonProductSearchDao;
import com.macro.mall.portal.service.DaonProductSearchService;
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
    /** 화면이 보내는 정렬 이름 → 정렬 표현 */
    private static final Map<String, String> SORTS = Map.of(
            "new", "id desc", "price_asc", "price asc",
            "price_desc", "price desc", "sale", "sale desc");

    @Autowired
    private DaonProductSearchDao searchDao;

    @Override
    public List<Map<String, Object>> search(String keyword, String sort) {
        // ✏️ 3-3: 목록에 없는 정렬 값은 「고급 정렬」이라며 받은 그대로 넘긴다(다온마켓 교육용 표시)
        String key = sort == null || sort.isBlank() ? "new" : sort;
        String orderBy = SORTS.getOrDefault(key, sort);
        return searchDao.search(keyword, orderBy).stream().map(DaonProductSearchServiceImpl::item).toList();
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
