package com.macro.mall.portal.dao;

import com.macro.mall.model.PmsProduct;
import com.macro.mall.security.securekit.SecureKitSwitch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

/**
 * [교육용 추가] 다온마켓 상품 검색 DAO(resources/dao/DaonProductSearchDao.xml)
 */
public interface DaonProductSearchDao {
    /** 다온마켓 3-2: 검색어를 바인딩한 XML 문장(searchBound) */
    List<PmsProduct> searchBound(@Param("keyword") String keyword, @Param("sort") String sort);

    /**
     * [랩 스위치] secure-kit.query-binding.enabled=false일 때만 쓴다(영상의 「끄면 레드」, make switch K=query-binding V=off).
     * 고치기 전 XML과 같은 문장을 자바에서 이어 붙인다. 수업에서 손으로 고칠 때는 이 메서드를 만들지 않는다.
     */
    @SelectProvider(type = LabUnboundSql.class, method = "search")
    @ResultMap("com.macro.mall.mapper.PmsProductMapper.BaseResultMap")
    List<PmsProduct> searchUnbound(@Param("keyword") String keyword, @Param("sort") String sort);

    /** 서비스가 부르는 입구(이름은 고치기 전과 같다): 스위치가 켜져 있으면(기본) 바인딩한 XML 문장을 쓴다 */
    default List<PmsProduct> search(String keyword, String sort) {
        return SecureKitSwitch.on("query-binding") ? searchBound(keyword, sort) : searchUnbound(keyword, sort);
    }

    /** [랩 스위치] 끈 상태 전용 문장 */
    class LabUnboundSql {
        public static String search(Map<String, Object> p) {
            return "select * from pms_product where delete_status = 0 and publish_status = 1"
                    + " and name like '%" + p.get("keyword") + "%' order by " + p.get("sort") + " limit 100";
        }
    }
}
