package com.macro.mall.portal.service;

import java.util.List;
import java.util.Map;

/**
 * [교육용 추가] 다온마켓 상품 검색(수업 3-2·3-3)
 */
public interface DaonProductSearchService {
    /**
     * 공개 상품을 검색어로 찾는다. 정렬 이름: new(신상품)·price_asc·price_desc·sale(판매량).
     * 받을 수 없는 값이면 IllegalArgumentException(컨트롤러가 400으로 바꾼다).
     */
    List<Map<String, Object>> search(String keyword, String sort);
}
