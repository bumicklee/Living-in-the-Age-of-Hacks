package com.macro.mall.security.crypto;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * [다온마켓 5-3 정답] 개인정보 칸을 저장할 때 잠그고 읽을 때 푸는 MyBatis 타입 핸들러.
 *
 * 매퍼 XML에서 칸마다 붙인다:
 * <pre>
 * &lt;result column="phone" jdbcType="VARCHAR" property="phone" typeHandler="com.macro.mall.security.crypto.EncryptedStringTypeHandler" /&gt;
 * #{phone,jdbcType=VARCHAR,typeHandler=com.macro.mall.security.crypto.EncryptedStringTypeHandler}
 * </pre>
 *
 * 주의: {@code Example}의 {@code andPhoneEqualTo(...)} 같은 조건은 이 핸들러를 거치지 않는다. 암호문과 비교하므로 찾지 못한다
 * (그래서 5-4에서 검색용 해시 칸을 둔다).
 */
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        FieldCrypto fc = FieldCrypto.instance();
        ps.setString(i, fc == null ? parameter : fc.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return open(rs.getString(columnName), "column:" + columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return open(rs.getString(columnIndex), "column:#" + columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return open(cs.getString(columnIndex), "column:#" + columnIndex);
    }

    private static String open(String stored, String where) {
        FieldCrypto fc = FieldCrypto.instance();
        return fc == null ? stored : fc.decrypt(stored, where);
    }
}
