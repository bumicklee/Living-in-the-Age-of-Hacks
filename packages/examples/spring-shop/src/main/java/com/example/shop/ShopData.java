package com.example.shop;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 가게 데이터(DB 대신 메모리). 회원 전화번호는 가짜 010-0000-NNNN 모양만 쓴다 */
@Component
public class ShopData {

    /** 주문 조회. 번호 형식을 확인하지 않고 쿼리에 붙이던 옛 코드를 흉내 낸다(형식이 틀리면 DB 오류가 그대로 올라온다) */
    public Map<String, Object> order(String id) {
        if (!id.matches("\\d{1,9}")) {
            throw new IllegalStateException("SQLSyntaxErrorException: Unknown column '" + id + "' in 'oms_order'");
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", Long.parseLong(id));
        o.put("status", "PAID");
        o.put("amount", 32000);
        return o;
    }

    /** 회원 상세(화면용: 전화번호 가운데를 가린다) */
    public Map<String, Object> member(long id) {
        if (id < 1 || id > 25) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", String.format("회원%02d", id));
        m.put("phone", String.format("010-****-%04d", id));
        return m;
    }
}
